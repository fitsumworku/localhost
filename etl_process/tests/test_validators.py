import pytest

from src.validators import validate_user_change


def test_validate_user_change_accepts_upsert():
	validate_user_change(
		{
			"operation": "upsert",
			"user_id": 1,
			"user_name": "Ada",
			"status": "ACTIVE",
			"is_blacklisted": "N",
		}
	)


def test_validate_user_change_rejects_missing_fields():
	with pytest.raises(ValueError):
		validate_user_change({"operation": "upsert", "user_id": 1})
