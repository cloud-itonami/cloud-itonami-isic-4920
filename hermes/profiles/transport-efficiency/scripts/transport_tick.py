"""Shim only. hermes runs --script through bash (.sh/.bash) or Python;
the tick itself is workspace cljs (this workspace does not add new .sh),
so this file exists to hand control to nbb and pass its exit code through.
0 = a finding landed and verified / 1 = verification red / 2 = could not measure."""
import subprocess, sys, os
W = os.path.expanduser("~/.hermes/profiles/transport-efficiency/workspace")
r = subprocess.run(["nbb", "--classpath", W, os.path.join(W, "tick.cljs")],
                   cwd=W, capture_output=True, text=True)
sys.stdout.write(r.stdout)
sys.stderr.write(r.stderr)
sys.exit(r.returncode)
