from __future__ import annotations

from typing import Any


def apply_user_change(connection: Any, change: dict[str, Any]) -> None:
	"""Apply one transformed user change to the OLAP dimension."""
	with connection.cursor() as cursor:
		if change["operation"] == "delete":
			cursor.execute(
				"DELETE FROM dim_user WHERE user_id = %s",
				(change["user_id"],),
			)
		else:
			cursor.execute(
				"""
				INSERT INTO dim_user (user_id, user_name, status, is_blacklisted)
				VALUES (%s, %s, %s, %s)
				ON CONFLICT (user_id) DO UPDATE SET
					user_name = EXCLUDED.user_name,
					status = EXCLUDED.status,
					is_blacklisted = EXCLUDED.is_blacklisted
				""",
				(
					change["user_id"],
					change["user_name"],
					change["status"],
					change["is_blacklisted"],
				),
			)
	connection.commit()
