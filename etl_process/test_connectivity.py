from pathlib import Path

import psycopg2
import yaml


CONFIG_PATH = Path(__file__).parent / "config" / "database.yaml"


def main() -> None:
	config = yaml.safe_load(CONFIG_PATH.read_text())
	for name in ("oltp", "olap"):
		settings = config[name]
		connection = psycopg2.connect(**settings)
		try:
			with connection.cursor() as cursor:
				cursor.execute("SELECT current_database()")
				print(f"{name}: {cursor.fetchone()[0]}")
		finally:
			connection.close()


if __name__ == "__main__":
	main()
