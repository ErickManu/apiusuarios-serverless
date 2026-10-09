from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from check_terraform_plan import check_plan, REQUIRED_RESOURCES, RETAINED_ATTACHMENTS


class PlanGuardTest(unittest.TestCase):
    def setUp(self):
        self.plan = {"configuration": {"root_module": {"resources": [
            {"address": address} for address in REQUIRED_RESOURCES]}}, "resource_changes": []}

    def change(self, address, actions, before=None, after=None):
        self.plan["resource_changes"].append({"address": address, "change": {
            "actions": actions, "before": before, "after": after}})

    def test_non_destructive_changes_pass(self):
        self.change("aws_sns_topic.notifications", ["create"])
        self.assertEqual(check_plan(self.plan), [("aws_sns_topic.notifications", ["create"])])

    def test_topic_delete_or_replacement_is_always_blocked(self):
        for actions in (["delete"], ["create", "delete"], ["delete", "create"]):
            with self.subTest(actions=actions):
                self.plan["resource_changes"] = []
                self.change("aws_sns_topic.notifications", actions)
                with self.assertRaises(ValueError):
                    check_plan(self.plan)

    def test_older_configuration_missing_topic_is_blocked(self):
        self.plan["configuration"]["root_module"]["resources"] = []
        with self.assertRaises(ValueError):
            check_plan(self.plan)

    def test_jar_replacement_in_same_bucket_passes(self):
        self.change("aws_s3_object.lambda_code", ["delete", "create"],
                    {"bucket": "code", "key": "old.jar"}, {"bucket": "code", "key": "new.jar"})
        self.assertEqual(len(check_plan(self.plan)), 1)

    def test_jar_deletion_or_different_bucket_is_blocked(self):
        for actions, bucket in ((["delete"], "code"), (["delete", "create"], "other")):
            with self.subTest(actions=actions, bucket=bucket):
                self.plan["resource_changes"] = []
                self.change("aws_s3_object.lambda_code", actions, {"bucket": "code"}, {"bucket": bucket})
                with self.assertRaises(ValueError):
                    check_plan(self.plan)

    def test_phase_one_keeps_attachments_and_creates_scoped_policy(self):
        self.change("aws_iam_role_policy.notification_runtime", ["create"], after={"role": "worker"})
        for address, arn in RETAINED_ATTACHMENTS.items():
            attachment = {"role": "worker", "policy_arn": arn}
            self.change(address, ["no-op"], before=attachment, after=attachment)
        self.assertEqual(check_plan(self.plan), [
            ("aws_iam_role_policy.notification_runtime", ["create"])])

    def test_attachment_retirement_or_replacement_is_blocked_even_with_scoped_policy(self):
        for address, arn in RETAINED_ATTACHMENTS.items():
            for actions in (["delete"], ["create", "delete"], ["delete", "create"]):
                with self.subTest(address=address, actions=actions):
                    self.plan["resource_changes"] = []
                    self.change("aws_iam_role_policy.notification_runtime", ["create"],
                                after={"role": "worker"})
                    self.change(address, actions, before={"role": "worker", "policy_arn": arn})
                    with self.assertRaisesRegex(ValueError, address):
                        check_plan(self.plan)

    def test_phase_two_configuration_is_blocked_until_guard_is_explicitly_updated(self):
        for address in RETAINED_ATTACHMENTS:
            with self.subTest(address=address):
                self.plan["configuration"]["root_module"]["resources"] = [
                    {"address": item} for item in REQUIRED_RESOURCES if item != address]
                with self.assertRaisesRegex(ValueError, address):
                    check_plan(self.plan)

    def test_deposed_deletion_cannot_be_hidden_by_current_instance_at_same_address(self):
        for address in ["aws_sns_topic.notifications", *RETAINED_ATTACHMENTS]:
            for delete_first in (True, False):
                with self.subTest(address=address, delete_first=delete_first):
                    self.plan["resource_changes"] = []
                    self.change(address, ["delete"])
                    self.plan["resource_changes"][-1]["deposed"] = "8e886c94"
                    self.change(address, ["create"])
                    if not delete_first:
                        self.plan["resource_changes"].reverse()
                    with self.assertRaisesRegex(ValueError, address):
                        check_plan(self.plan)

    def test_other_infrastructure_deletion_is_blocked(self):
        self.change("aws_sqs_queue.notifications", ["delete"])
        with self.assertRaises(ValueError):
            check_plan(self.plan)


if __name__ == "__main__":
    unittest.main()
