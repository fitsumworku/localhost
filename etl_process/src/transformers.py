from __future__ import annotations

from typing import Any


def transform_user_event(event: dict[str, Any]) -> dict[str, Any] | None:
	"""Convert a Debezium envelope into a DIM_USER row operation."""
	payload = event.get("payload", event)
	operation = payload.get("op")

	if operation == "d":
		before = payload.get("before") or {}
		return {"operation": "delete", "user_id": before["user_id"]}

	if operation not in {"c", "r", "u"}:
		return None

	row = payload.get("after")
	if not row:
		return None

	return {
		"operation": "upsert",
		"user_id": row["user_id"],
		"user_name": row["name"],
		"status": row["status"],
		"is_blacklisted": "Y" if row["status"] == "SUSPENDED" else "N",
	}
