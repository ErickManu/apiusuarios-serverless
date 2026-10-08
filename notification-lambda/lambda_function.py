
import json
import os
import logging
import boto3

logger = logging.getLogger()
logger.setLevel(logging.INFO)

ses = boto3.client("ses")

SENDER_EMAIL = os.environ["SENDER_EMAIL"]


def lambda_handler(event, context):
    failures = []

    for record in event.get("Records", []):
        message_id = record["messageId"]

        try:
            message = json.loads(record["body"])

            email = message["email"]
            subject = message["subject"]
            body = message["message"]

            response = ses.send_email(
                Source=SENDER_EMAIL,
                Destination={
                    "ToAddresses": [email]
                },
                Message={
                    "Subject": {
                        "Data": subject,
                        "Charset": "UTF-8"
                    },
                    "Body": {
                        "Text": {
                            "Data": body,
                            "Charset": "UTF-8"
                        }
                    }
                }
            )

            logger.info(
                "Mensaje SQS %s procesado. SES ID: %s",
                message_id,
                response["MessageId"]
            )

        except Exception:
            logger.exception(
                "Error procesando mensaje SQS %s",
                message_id
            )
            failures.append({
                "itemIdentifier": message_id
            })

    return {
        "batchItemFailures": failures
    }
