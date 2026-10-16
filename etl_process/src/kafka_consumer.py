from __future__ import annotations

import json
from collections.abc import Iterator
from typing import Any

from kafka import KafkaConsumer


def user_events(
	bootstrap_servers: str = "localhost:9092",
	topic: str = "debezium.oltp.public.users",
	group_id: str = "etl-users",
) -> Iterator[dict[str, Any]]:
	consumer = KafkaConsumer(
		topic,
		bootstrap_servers=bootstrap_servers,
		group_id=group_id,
		auto_offset_reset="earliest",
		enable_auto_commit=True,
		value_deserializer=lambda value: json.loads(value.decode("utf-8")),
	)
	try:
		for message in consumer:
			yield message.value
	finally:
		consumer.close()
