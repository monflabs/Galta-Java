# Proper Tail Calls

Shows that this engine, like most JS engines, does not implement proper tail-call optimization: a small recursive `factorial` works, but deep recursion (`factorial(10000)`) overflows the stack - caught here so the demo reports the failure instead of crashing.
