import io
import traceback
from contextlib import redirect_stderr, redirect_stdout


def run(source, bridge):
    output = io.StringIO()

    def read_input(prompt=""):
        if prompt:
            output.write(str(prompt))
            output.flush()
        return bridge.readLine(str(prompt))

    def cancellation_trace(_frame, _event, _arg):
        if bridge.isCancelled():
            raise KeyboardInterrupt("Execution cancelled")
        return cancellation_trace

    namespace = {"__name__": "__main__", "__builtins__": dict(__builtins__)}
    namespace["__builtins__"]["input"] = read_input

    try:
        import sys

        sys.settrace(cancellation_trace)
        with redirect_stdout(output), redirect_stderr(output):
            exec(compile(source, "<script>", "exec"), namespace, namespace)
    except BaseException as error:
        if isinstance(error, KeyboardInterrupt) and bridge.isCancelled():
            output.write("\nExecution cancelled.\n")
        else:
            traceback.print_exc(file=output)
    finally:
        sys.settrace(None)

    result = output.getvalue()
    return result if result else "Program finished with no output.\n"
