#!/bin/bash
input_folder="datasets/a"
output_folder="outputs"

for input_file in "$input_folder"/*.txt; do
    instance_name=$(basename "$input_file")  # Pega apenas o nome do arquivo
    output_file="$output_folder/$instance_name"

    echo "Verificando $instance_name..."
    python checker.py "$input_file" "$output_file"
    echo "-----------------------------"
done
