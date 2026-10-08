"""Offline tests: boto3 and SES are replaced before importing the handler."""
import importlib.util
import json
from pathlib import Path
import sys
import unittest
from unittest.mock import Mock, patch

fake_boto3 = Mock()
fake_config_module = Mock()
spec = importlib.util.spec_from_file_location(
    "notification_handler", Path(__file__).with_name("lambda_function.py"))
handler = importlib.util.module_from_spec(spec)
with patch.dict(sys.modules, {"boto3": fake_boto3, "botocore": Mock(),
                              "botocore.config": fake_config_module}):
    spec.loader.exec_module(handler)


class NotificationHandlerTest(unittest.TestCase):
    def setUp(self):
        self.ses = Mock()
        self.ses.send_email.return_value = {"MessageId": "ses-test"}
        self.context = Mock()
        self.context.get_remaining_time_in_millis.return_value = 30000
        self.patch_ses = patch.object(handler, "ses", self.ses)
        self.patch_sender = patch.object(handler, "SENDER_EMAIL", "sender@example.invalid")
        self.patch_ses.start()
        self.patch_sender.start()
        self.addCleanup(self.patch_ses.stop)
        self.addCleanup(self.patch_sender.stop)

    def record(self, identifier, **fields):
        payload = {"email": "recipient@example.invalid", "subject": "Aviso", "message": "Hola ñ"}
        payload.update(fields)
        return {"messageId": identifier, "body": json.dumps(payload)}

    def run_records(self, *records):
        return handler.lambda_handler({"Records": list(records)}, self.context)

    def test_raw_sns_message_is_sent_as_utf8(self):
        self.assertEqual(self.run_records(self.record("ok")), {"batchItemFailures": []})
        request = self.ses.send_email.call_args.kwargs
        self.assertEqual(request["Source"], "sender@example.invalid")
        self.assertEqual(request["Destination"]["ToAddresses"], ["recipient@example.invalid"])
        self.assertEqual(request["Message"]["Body"]["Text"], {"Data": "Hola ñ", "Charset": "UTF-8"})

    def test_ses_failure_retries_only_failed_record_and_continues_batch(self):
        error = RuntimeError("private payload must not be logged")
        error.response = {"Error": {"Code": "MessageRejected"}}
        self.ses.send_email.side_effect = [{"MessageId": "first"}, error, {"MessageId": "last"}]
        with self.assertLogs(handler.logger, level="INFO") as logs:
            result = self.run_records(self.record("one"), self.record("bad"), self.record("three"))
        self.assertEqual(result, {"batchItemFailures": [{"itemIdentifier": "bad"}]})
        self.assertEqual(self.ses.send_email.call_count, 3)
        self.assertIn("MessageRejected", " ".join(logs.output))
        self.assertNotIn("private payload", " ".join(logs.output))
        self.assertNotIn("recipient@example.invalid", " ".join(logs.output))

    def test_invalid_payloads_do_not_call_ses_and_do_not_block_valid_message(self):
        invalid = ["not-json", "null", "[]", json.dumps({"email": 12}),
                   self.record("blank", message=" ")["body"],
                   self.record("header", subject="One\r\nTwo")["body"]]
        records = [{"messageId": str(index), "body": body} for index, body in enumerate(invalid)]
        with self.assertLogs(handler.logger, level="ERROR"):
            result = self.run_records(*records, self.record("valid"))
        self.assertEqual(result["batchItemFailures"], [{"itemIdentifier": str(i)} for i in range(len(invalid))])
        self.ses.send_email.assert_called_once()

    def test_low_time_reports_unprocessed_records_without_resending_success(self):
        self.context.get_remaining_time_in_millis.side_effect = [30000, 6000]
        with self.assertLogs(handler.logger, level="WARNING"):
            result = self.run_records(self.record("sent"), self.record("pending"), self.record("last"))
        self.assertEqual(result, {"batchItemFailures": [
            {"itemIdentifier": "pending"}, {"itemIdentifier": "last"}]})
        self.ses.send_email.assert_called_once()

    def test_missing_sender_reports_failure(self):
        with patch.object(handler, "SENDER_EMAIL", ""), self.assertLogs(handler.logger, level="ERROR"):
            self.assertEqual(self.run_records(self.record("bad")),
                             {"batchItemFailures": [{"itemIdentifier": "bad"}]})
        self.ses.send_email.assert_not_called()

    def test_invalid_identifier_fails_before_sending_any_record(self):
        with self.assertLogs(handler.logger, level="ERROR"), self.assertRaises(ValueError):
            self.run_records(self.record("valid"), {"body": "{}"})
        self.ses.send_email.assert_not_called()

    def test_request_timeout_budget_leaves_time_for_partial_response(self):
        options = fake_config_module.Config.call_args.kwargs
        attempts = options["retries"]["total_max_attempts"]
        request_budget_ms = (options["connect_timeout"] + options["read_timeout"]) * attempts * 1000
        self.assertGreater(attempts, 0)
        self.assertLessEqual(request_budget_ms + 1000, handler.MIN_REMAINING_TIME_MS)


if __name__ == "__main__":
    unittest.main()
