
import json
import os
import logging
import boto3
from botocore.config import Config

logger = logging.getLogger()
logger.setLevel(logging.INFO)

ses = boto3.client("ses", config=Config(
    connect_timeout=2,
    read_timeout=3,
    retries={"mode": "standard", "total_max_attempts": 1},
))

SENDER_EMAIL = os.environ.get("SENDER_EMAIL", "")

# Leave enough time for one bounded SES request and the partial batch response.
MIN_REMAINING_TIME_MS = 7000


def parse_message(body):
    message = json.loads(body)
    if not isinstance(message, dict):
        raise ValueError("El mensaje debe ser un objeto JSON")
    for field in ("email", "subject", "message"):
        if not isinstance(message.get(field), str) or not message[field].strip():
            raise ValueError("Campos de notificación inválidos")
    if "\r" in message["subject"] or "\n" in message["subject"]:
        raise ValueError("El asunto debe tener una sola línea")
    return message


def lambda_handler(event, context):
    failures = []

    records = event["Records"]
    # Validate identifiers before sending anything. An invalid response identifier
    # would cause Lambda to retry even the records already sent successfully.
    if any(not isinstance(record.get("messageId"), str) or not record["messageId"]
           for record in records):
        logger.error("Evento SQS sin identificadores válidos")
        raise ValueError("Evento SQS inválido")

    for index, record in enumerate(records):
        message_id = record["messageId"]

        if context.get_remaining_time_in_millis() < MIN_REMAINING_TIME_MS:
            failures.extend({"itemIdentifier": pending["messageId"]}
                            for pending in records[index:])
            logger.warning("Tiempo insuficiente; %s mensajes pendientes", len(records) - index)
            break

        try:
            if not SENDER_EMAIL.strip():
                raise ValueError("SENDER_EMAIL no configurado")
            message = parse_message(record["body"])

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

        except Exception as error:
            # Do not log email addresses, bodies or raw AWS exception messages.
            error_code = getattr(error, "response", {}).get("Error", {}).get("Code", type(error).__name__)
            logger.error("Error procesando mensaje SQS %s. Tipo: %s", message_id, error_code)
            failures.append({
                "itemIdentifier": message_id
            })

    return {
        "batchItemFailures": failures
    }
