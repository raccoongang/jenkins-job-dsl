if [ "x$NUM_REDUCE_TASKS" = "x" ]; then
    NUM_REDUCE_TASKS=$(( $NUM_TASK_CAPACITY * 2 ))
fi

analytics-configuration/automation/run-automated-task.sh \
  InsertToMysqlAllVideoTask --local-scheduler \
  --interval $(date +%Y-%m-%d -d "$FROM_DATE")-$(date +%Y-%m-%d -d "$TO_DATE") \
  --n-reduce-tasks $NUM_REDUCE_TASKS
