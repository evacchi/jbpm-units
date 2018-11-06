package org.jbpm.units;

import java.util.Collection;
import java.util.Collections;

import org.jbpm.units.internal.VariableBinder;
import org.jbpm.units.signals.Event;
import org.kie.api.UnitInstance;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.process.ProcessInstance;

/**
 * A UnitSession for BPM Processes, delegating internally to
 * jBPM's ProcessInstance
 */
public class ProcessUnitInstance implements UnitInstance {

    public interface Signal extends UnitInstance.Signal {

    }

    private final KieSession session;
    private final ProcessUnit processUnit;
    private final VariableBinder variableBinder;
    final ProcessInstance processInstance;
    State state;

    public ProcessUnitInstance(
            ProcessUnit processUnit,
            KieSession session) {
        String processId = processUnit.getUnitIdentity().toString();
        this.processUnit = processUnit;
        this.session = session;
        this.variableBinder = new VariableBinder(processUnit);
        this.processInstance =
                session.createProcessInstance(
                        processId, variableBinder.asMap());

        this.state = State.Created;
        this.processUnit.onCreate();
    }

    public ProcessUnit unit() {
        return processUnit;
    }

    public void start() {
        try {
            this.state = State.Entering;
            processUnit.onEnter();
            this.state = State.Running;
            processUnit.onStart();
            session.startProcessInstance(processInstance.getId());
        } catch (Throwable t) {
            this.state = State.Faulted;
            processUnit.onFault(t);
            this.state = State.Completed;
            processUnit.onEnd();
        } finally {
            variableBinder.updateBindings(processInstance);
        }
    }

    @Override
    public void halt() {
        session.abortProcessInstance(id());
    }

    @Override
    public void suspend() {
        state = State.Suspended;
        processUnit.onSuspend();
    }

    @Override
    public void resume() {
        state = State.Resuming;
        processUnit.onResume();
    }

    public long id() {
        return processInstance.getId();
    }

    @Override
    public void signal(UnitInstance.Signal signal) {
        if (signal instanceof Event) {
            Event sig = (Event) signal;
            processInstance.signalEvent(sig.type(), sig.payload());
        }
        // drop anything else
    }

    @Override
    public void yield(UnitInstance next) {
        // unsupported
    }

    @Override
    public Collection<UnitInstance> references() {
        // processes that point at this (currently unsupported)
        return Collections.emptyList();
    }

    @Override
    public State state() {
        return state;
    }
}
