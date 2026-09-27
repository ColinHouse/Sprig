#!/usr/bin/env python3
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
from eval_lib import check_file, expect_file_stdout, forbid, source_text

project = Path(sys.argv[1])
check_file(project / "main.spr")
forbid(source_text(project), "items=3", "compute the count from the parsed document")
fixture = project / "fixture.json"
fixture.write_text('{"items":[1,2,3],"name":"agent"}', encoding="utf-8")
expect_file_stdout(project / "main.spr", "items=3", fixture)
