package eu.transkribus.appserver.logic.executors;

import eu.transkribus.core.model.beans.enums.Task;

public abstract class AJobExecutor implements IJobExecutor {
	private String name;
	private Task task;
	
	public String getName(){
		return name;
	}
	public Task getTask(){
		return task;
	}
}
