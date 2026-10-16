from src.transformers import transform_user_event


def test_transform_snapshot_user():
	event = {
		"payload": {
			"op": "r",
			"after": {"user_id": 1, "name": "Ada", "status": "ACTIVE"},
		}
	}
	assert transform_user_event(event) == {
		"operation": "upsert",
		"user_id": 1,
		"user_name": "Ada",
		"status": "ACTIVE",
		"is_blacklisted": "N",
	}


def test_transform_delete_user():
	event = {"payload": {"op": "d", "before": {"user_id": 1}}}
	assert transform_user_event(event) == {"operation": "delete", "user_id": 1}
