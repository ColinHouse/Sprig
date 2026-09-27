#!/usr/bin/env python3
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
from eval_lib import check_file, expect_file_stdout, forbid, source_text

project = Path(sys.argv[1])
check_file(project / "main.spr")
forbid(source_text(project), "total=3", "print the computed result, not the expected literal")
forbid(source_text(project), "import java", "this task is pure Sprig policy")
expect_file_stdout(project / "main.spr", "total=3\npositive=3")
