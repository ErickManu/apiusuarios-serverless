"""Reject unexpected Terraform deletions without printing plan values/secrets."""
import json
import sys

RETAINED_ATTACHMENTS = {
    "aws_iam_role_policy_attachment.notification_basic":
        "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole",
    "aws_iam_role_policy_attachment.notification_sqs":
        "arn:aws:iam::aws:policy/service-role/AWSLambdaSQSQueueExecutionRole",
}
REQUIRED_RESOURCES = {
    "aws_sns_topic.notifications",
    "aws_sqs_queue.notifications",
    "aws_sqs_queue.notifications_dlq",
    "aws_sns_topic_subscription.notifications",
    "aws_lambda_event_source_mapping.notification_sqs",
    "aws_iam_role_policy.backend_sns",
    "aws_iam_role_policy.notification_runtime",
    "aws_iam_role_policy.notification_ses",
} | RETAINED_ATTACHMENTS.keys()


def check_plan(plan):
    configured = {resource["address"] for resource in
                  plan.get("configuration", {}).get("root_module", {}).get("resources", [])}
    missing = REQUIRED_RESOURCES - configured
    if missing:
        raise ValueError("Falta configuración requerida: " + ", ".join(sorted(missing)))

    # A current and a deposed instance can share an address. Check every entry;
    # indexing by address would silently hide one of their actions.
    changes = [(resource["address"], resource["change"])
               for resource in plan.get("resource_changes", [])]
    for address, change in changes:
        actions = change.get("actions", [])
        if "delete" not in actions:
            continue
        # Only replacing the content-addressed JAR in its existing bucket is expected.
        if address == "aws_s3_object.lambda_code" and set(actions) == {"create", "delete"}:
            before, after = change.get("before") or {}, change.get("after") or {}
            if before.get("bucket") and before["bucket"] == after.get("bucket"):
                continue
        # Phase 1 never retires managed attachments, even if the inline policy exists.
        raise ValueError("Eliminación/reemplazo no autorizado: " + address)

    return [(address, change.get("actions", []))
            for address, change in sorted(changes, key=lambda item: item[0])
            if change.get("actions") != ["no-op"]]


def main():
    try:
        changes = check_plan(json.load(sys.stdin))
    except (ValueError, KeyError, TypeError) as error:
        print("Plan bloqueado: " + str(error), file=sys.stderr)
        return 1
    print("Plan revisado: " + str(len(changes)) + " cambios; sin eliminaciones inesperadas.")
    for address, actions in changes:
        print(address + ": " + ",".join(actions))
    return 0


if __name__ == "__main__":
    sys.exit(main())
