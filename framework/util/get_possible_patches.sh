projects="Chart Lang Math Time Closure Mockito"
D4J_PATH="$(pwd)/../bin/defects4j"
output_file="$(pwd)/possible_patches.csv"
# delete and recreate output file
[ -e "$output_file" ] && rm $output_file && touch $output_file


for project in $projects; do
  bids=$(cat "../projects/$project/commit-db" | cut -d',' -f1)
  total_bids=$(echo $bids | wc -w)
  patch_path="$(pwd)/../projects/$project/patches"
  for bid in $bids; do
    # checkout project into temporary directory
    tmp_dir="/tmp/defects4j/$project-${bid}-$$"
    mkdir -p "$tmp_dir"
    if [ $? -ne 0 ]; then
      echo "Could not create temporary directory under /tmp. Perhaps you don't have permissions?"
      return 1
    fi
    $D4J_PATH checkout -p $project -v ${bid}b -w $tmp_dir &> /dev/null
    pushd $tmp_dir > /dev/null

    possible_patches=()
    echo "$project-$bid"
    # try all other patches
    for bid2 in $bids; do
      echo -ne "$bid2/$total_bids...\r"

      if [ "$bid" == "$bid2" ]; then
        continue
      fi
      patch="$patch_path/$bid2.src.patch"
      git apply $patch &> /dev/null
      # patch failed
      if [ $? -ne 0 ]; then
        git reset --hard tags/D4J_${project}_${bid}_BUGGY_VERSION &> /dev/null
        continue
      fi
      # try to compile the code after the patch
      $D4J_PATH compile &> /dev/null
      # compile success
      if [ $? -eq 0 ]; then
        possible_patches+=("$bid2")
      fi
      git reset --hard tags/D4J_${project}_${bid}_BUGGY_VERSION &> /dev/null
    done
    echo "$bid2/$total_bids...OK"
    popd > /dev/null
    patches=$(echo ${possible_patches[@]} | tr ' ' ':')
    echo "$project,$bid,$patches" >> $output_file

    # don't leave any temporary directories hanging about!
    rm -rf "$tmp_dir"
  done
done