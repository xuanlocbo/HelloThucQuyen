CREATE TABLE session_state (slot INTEGER PRIMARY KEY CHECK(slot=1), payload TEXT NOT NULL);
CREATE TABLE completions (task_id TEXT PRIMARY KEY NOT NULL, kind TEXT NOT NULL CHECK(kind IN ('validated_completion','explicit_participation_done')));
CREATE TABLE reward_ledger (task_id TEXT PRIMARY KEY NOT NULL REFERENCES completions(task_id), item_id TEXT NOT NULL, quantity INTEGER NOT NULL CHECK(quantity=1), claimed INTEGER NOT NULL DEFAULT 0 CHECK(claimed IN (0,1)));
CREATE TABLE garden_inventory (item_id TEXT PRIMARY KEY NOT NULL, quantity INTEGER NOT NULL CHECK(quantity>=0));
CREATE TABLE garden_placements (task_id TEXT PRIMARY KEY NOT NULL REFERENCES reward_ledger(task_id), x REAL NOT NULL CHECK(x>=0 AND x<=1), y REAL NOT NULL CHECK(y>=0 AND y<=1));
