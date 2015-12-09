/*
 * Copyright (c) 2011-2016 Pivotal Software Inc, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package reactor.aeron.subscriber;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.Timers;
import reactor.aeron.Context;
import reactor.fn.Consumer;
import reactor.fn.Pausable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Anatoly Kadyshev
 */
public class HeartbeatWatchdog {

	private static final Logger logger = LoggerFactory.getLogger(HeartbeatWatchdog.class);

	private final ServiceMessageHandler serviceMessageHandler;

	private final SessionReaper sessionReaper;

	private final SessionTracker<? extends Session> sessionTracker;

	private volatile Pausable pausable;

	private final long heartbeatTimeoutNs;

	class SessionReaper implements Consumer<Long> {

		private final List<Session> heartbeatLostSessions = new ArrayList<>();

		@Override
		public void accept(Long value) {
			long now = System.nanoTime();
			for (Session session : sessionTracker.getSessions()) {
				if (session.getLastHeartbeatTimeNs() > 0 &&
						session.getLastHeartbeatTimeNs() - now > heartbeatTimeoutNs) {
					heartbeatLostSessions.add(session);
				}
			}

			for (int i = 0; i < heartbeatLostSessions.size(); i++) {
				//TODO: Report heartbeat loss
				serviceMessageHandler.handleCancel(heartbeatLostSessions.get(i).getSessionId());
			}

			heartbeatLostSessions.clear();
		}

	}

	public HeartbeatWatchdog(Context context, ServiceMessageHandler serviceMessageHandler,
							 SessionTracker<? extends Session> sessionTracker) {
		this.serviceMessageHandler = serviceMessageHandler;
		this.sessionTracker = sessionTracker;
		this.sessionReaper = new SessionReaper();
		this.heartbeatTimeoutNs = TimeUnit.MILLISECONDS.toNanos(context.heartbeatIntervalMillis());
	}

	public void start() {
		this.pausable = Timers.global().schedule(sessionReaper, (heartbeatTimeoutNs * 3) / 2, TimeUnit.NANOSECONDS);

		logger.debug("HeartbeatWatchdog started");
	}

	public void shutdown() {
		if (pausable != null) {
			pausable.cancel();
		}

		logger.debug("HeartbeatWatchdog shutdown");
	}

}