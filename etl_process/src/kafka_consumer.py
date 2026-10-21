from __future__ import annotations

import json
from collections.abc import Iterator
from typing import Any

from kafka import KafkaConsumer


def user_events(
	bootstrap_servers: str = "localhost:9092",
	topic: str = "debezium.oltp.public.users",
	group_id: str = "etl-users-smoke-test",
	idle_limit: int = 10,
) -> Iterator[dict[str, Any]]:
	consumer = KafkaConsumer(
		topic,
		bootstrap_servers=bootstrap_servers,
		group_id=group_id,
		auto_offset_reset="earliest",
		enable_auto_commit=True,
		request_timeout_ms=15000,
		consumer_timeout_ms=1000,
		value_deserializer=lambda value: json.loads(value.decode("utf-8")),
	)
	idle_polls = 0
	try:
		while idle_polls < idle_limit:
			records = consumer.poll(timeout_ms=1000, max_records=100)
			messages = [message for batch in records.values() for message in batch]
			if not messages:
				idle_polls += 1
				continue
			idle_polls = 0
			for message in messages:
				yield message.value
	finally:
		consumer.close()
