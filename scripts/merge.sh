#!/bin/bash

# Check if two arguments are provided
if [ $# -ne 2 ]; then
  echo "Usage: $0 file1 file2"
  exit 1
fi

# Assign file names
file1="$1"
file2="$2"

# Check if files exist
if [ ! -f "$file1" ]; then
  echo "Error: file1 '$file1' does not exist"
  exit 1
fi

if [ ! -f "$file2" ]; then
  echo "Error: file2 '$file2' does not exist"
  exit 1
fi

# Perform merge using paste command
paste -d ',' "$file1" "$file2" > "$file1.merged"

# Print success message
echo "Merged files into '$file1.merged'"
