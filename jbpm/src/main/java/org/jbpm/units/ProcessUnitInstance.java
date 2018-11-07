package org.jbpm.units;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;

import org.jbpm.units.internal.VariableBinder;
import org.jbpm.workflow.instance.WorkflowProcessInstance;
import org.kie.api.UnitInstance;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.process.EventListener;
import org.kie.api.runtime.process.ProcessInstance;

/**
 * A UnitSession for BPM Processes, delegating internally to
 * jBPM's ProcessInstance
 */
public class ProcessUnitInstance implements UnitInstance {

    public interface Signal extends UnitInstance.Signal {

    }

    private final Deque<UnitInstance.Signal> pendingSignals;
    private final KieSession session;
    private final ProcessUnit processUnit;
    private final VariableBinder variableBinder;
    public final WorkflowProcessInstance processInstance;
    State state;

    public ProcessUnitInstance(
            ProcessUnit processUnit,
            KieSession session) {
        String processId = processUnit.getUnitIdentity().toString();
        this.processUnit = processUnit;
        this.session = session;
        this.variableBinder = new VariableBinder(processUnit);
        this.processInstance =
                (WorkflowProcessInstance) session.createProcessInstance(
                        processId, variableBinder.asMap());
        this.processInstance.addEventListener("workItemCompleted",
                                              new EventListener() {

                                                  private String[] eventTypes = {"workItemCompleted"};

                                                  @Override
                                                  public void signalEvent(String type, Object event) {
                                                      state = State.Resuming;
                                                  }

                                                  @Override
                                                  public String[] getEventTypes() {
                                                      return eventTypes;
                                                  }
                                              }

                , false);

        this.state = State.Created;
        this.processUnit.onCreate();
        pendingSignals = new ArrayDeque<>();
    }

    public ProcessUnit unit() {
        return processUnit;
    }

    public void start() {
        nextState();
        variableBinder.updateBindings(processInstance);
    }

    private void nextState() {
        switch (state) {
            case Created:
                run();
                if (processInstance.getState() == ProcessInstance.STATE_ACTIVE) {
                    this.state = State.Suspended;
                    processUnit.onSuspend();
                }
                break;
            case Resuming:
                resume();
                break;
            default:
                throw new IllegalStateException(state.name());
        }
    }

//    private void run() {
//        try {
//            doRun();
//        } catch (Throwable t) {
//            fail(t);
//        } finally {
//            variableBinder.updateBindings(processInstance);
//        }
//    }

    private void fail(Throwable t) {
        this.state = State.Faulted;
        processUnit.onFault(t);
        this.state = State.Completed;
        processUnit.onEnd();
    }

    private void run() {
        processUnit.onStart();
        this.state = State.Entering;
        processUnit.onEnter();
        this.state = State.Running;
        session.startProcessInstance(processInstance.getId());
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
        state = State.ReEntering;
        processUnit.onReEnter();
        this.state = State.Running;
        UnitInstance.Signal sig = pendingSignals.poll();
        while (sig != null) {
            sig.exec(this);
            sig = pendingSignals.poll();
        }
    }

    public long id() {
        return processInstance.getId();
    }

    @Override
    public void signal(UnitInstance.Signal signal) {
        if (signal instanceof ProcessUnitInstance.Signal) {
        }
        pendingSignals.add( signal);
        state = State.Resuming;
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
