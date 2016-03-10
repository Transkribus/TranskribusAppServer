package eu.transkribus.appserver.logic.executors;

import eu.transkribus.core.model.beans.job.TrpJobStatus;

public interface IJobExecutor {
	public void shutdown();
	public void submit(TrpJobStatus j);
}
