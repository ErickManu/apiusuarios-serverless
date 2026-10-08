import copy
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from check_terraform_plan import check_plan, REQUIRED_RESOURCES, RETIRED_ATTACHMENTS


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

    def test_authorized_attachment_migration_requires_policy_on_same_role(self):
        self.change("aws_iam_role_policy.notification_runtime", ["create"], after={"role": "worker"})
        for address, arn in RETIRED_ATTACHMENTS.items():
            self.change(address, ["delete"], before={"role": "worker", "policy_arn": arn})
        self.assertEqual(len(check_plan(self.plan)), 3)
        unsafe = copy.deepcopy(self.plan)
        unsafe["resource_changes"][0]["change"]["after"]["role"] = "other"
        with self.assertRaises(ValueError):
            check_plan(unsafe)

    def test_other_infrastructure_deletion_is_blocked(self):
        self.change("aws_sqs_queue.notifications", ["delete"])
        with self.assertRaises(ValueError):
            check_plan(self.plan)


if __name__ == "__main__":
    unittest.main()
