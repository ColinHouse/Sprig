# CSV text-table example

From this directory, with a built compiler at `../../bin/sprig`:

```sh
../../bin/sprig resolve
../../bin/sprig run --offline -- contacts.csv
```

On Windows use `..\..\bin\sprig.cmd`. An installed SDK containing this
example can use `sprig` from PATH instead. The program creates or replaces the
chosen output (default `contacts.csv`) with a UTF-8 CSV file containing headers,
quoted commas, a multiline cell and non-ASCII text. It then reads the file:

```text
CSV rows=2
世界: tea, cake
Zoë: first line
second line
```

See the [package contract](../../libraries/sprig-csv/README.md) for delimiter,
header, blank-record, error and memory behavior. The package ships in the SDK
from v0.9.0-beta.1; it is not in the registry yet.
