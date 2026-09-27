#!/usr/bin/env python3
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
from eval_lib import check_file, expect_file_stdout, source_text
from eval_lib import require

project = Path(sys.argv[1])
check_file(project / "main.spr")
require("Square(radius" not in source_text(project), "do not change the Shape cases")
expect_file_stdout(project / "main.spr", "12\n9")
