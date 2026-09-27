#!/usr/bin/env python3
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
from eval_lib import check_file, expect_file_stdout, forbid, source_text

project = Path(sys.argv[1])
check_file(project / "main.spr")
forbid(source_text(project), "import java", "solve this with Sprig composition, not a host adapter")
forbid(source_text(project), "TODO", "remove the placeholder")
expect_file_stdout(project / "main.spr", "dog says woof\ncat says meow")
