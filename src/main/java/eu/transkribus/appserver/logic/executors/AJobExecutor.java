package eu.transkribus.appserver.logic.executors;

import eu.transkribus.core.model.beans.enums.Task;

public abstract class AJobExecutor implements IJobExecutor {
	protected final String type;
	protected final Task task;
	
	protected AJobExecutor(Task task, String type){
		this.type = type;
		this.task = task;
	}

	public Task getTask(){
		return task;
	}
	
	public String getType(){
		return type;
	}
}
