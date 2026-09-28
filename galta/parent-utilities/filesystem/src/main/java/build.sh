#!/bin/bash

echo "=== Building Dual FileSystem Implementation ==="
echo ""

# Compile all Java files
echo "Compiling Java files..."
javac *.java

if [ $? -eq 0 ]; then
    echo "✓ Compilation successful!"
    echo ""
    echo "To run the demo:"
    echo "  java DualFileSystemDemo"
    echo ""
    echo "Or try each filesystem separately:"
    echo "  # File-based example"
    echo "  java -cp . -c 'import java.nio.file.*; Files.write(Paths.get(\"file-impl:///tmp/test.txt\"), \"test\".getBytes());'"
    echo ""
    echo "  # Memory-based example"  
    echo "  java -cp . -c 'import java.nio.file.*; Files.write(Paths.get(\"memory:///test.txt\"), \"test\".getBytes());'"
else
    echo "✗ Compilation failed!"
    exit 1
fi
