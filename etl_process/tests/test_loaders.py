from unittest.mock import MagicMock

from src.loaders import apply_user_change


def test_apply_user_change_upserts_and_commits():
	cursor = MagicMock()
	connection = MagicMock()
	connection.cursor.return_value.__enter__.return_value = cursor
	apply_user_change(
		connection,
		{
			"operation": "upsert",
			"user_id": 1,
			"user_name": "Ada",
			"status": "ACTIVE",
			"is_blacklisted": "N",
		},
	)
	cursor.execute.assert_called_once()
	connection.commit.assert_called_once_with()
