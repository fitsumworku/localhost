from __future__ import annotations

from typing import Any


def validate_user_change(change: dict[str, Any]) -> None:
	required = {"operation", "user_id"}
	missing = required - change.keys()
	if missing:
		raise ValueError(f"Missing user fields: {sorted(missing)}")

	if change["operation"] not in {"upsert", "delete"}:
		raise ValueError(f"Unsupported operation: {change['operation']}")

	if change["operation"] == "upsert":
		required_upsert = {"user_name", "status", "is_blacklisted"}
		missing_upsert = required_upsert - change.keys()
		if missing_upsert:
			raise ValueError(f"Missing upsert fields: {sorted(missing_upsert)}")
