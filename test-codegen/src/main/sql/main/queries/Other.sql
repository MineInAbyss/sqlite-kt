-- test
SELECT *
FROM spawn_data;

-- fun getData(id: Int)
SELECT data
FROM spawn_data
WHERE id = :id;