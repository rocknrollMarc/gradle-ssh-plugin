package org.hidetake.gradle.ssh.internal

import com.jcraft.jsch.Channel
import groovy.util.logging.Slf4j
import org.hidetake.gradle.ssh.api.SshService
import org.hidetake.gradle.ssh.api.SshSpec
import org.hidetake.gradle.ssh.internal.session.ChannelManager
import org.hidetake.gradle.ssh.internal.session.SessionManager

/**
 * Default implementation of {@link SshService}.
 *
 * @author hidetake.org
 *
 */
@Singleton
@Slf4j
class DefaultSshService implements SshService {
    @Override
    void execute(SshSpec sshSpec) {
        assert sshSpec.dryRun == Boolean.FALSE, 'dryRun should be false'

        def sessionManager = new SessionManager(sshSpec)
        def channelManager = new ChannelManager()
        try {
            sshSpec.sessionSpecs.collect { sessionSpec ->
                def session = sessionManager.create(sessionSpec.remote)

                def operation = sessionSpec.operationClosure
                operation.delegate = new DefaultOperationHandler(sshSpec, sessionSpec, session, channelManager)
                operation.resolveStrategy = Closure.DELEGATE_FIRST
                operation
            }.each {
                it.call()
            }

            channelManager.waitForPending { Channel channel ->
                log.info("Channel #${channel.id} has been closed with exit status ${channel.exitStatus}")
            }
            channelManager.validateExitStatus()
        } finally {
            channelManager.disconnect()
            sessionManager.disconnect()
        }
    }
}
