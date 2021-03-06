package org.jbpm.example;

import java.util.Collections;

import org.jbpm.example.units.FailingUnit;
import org.jbpm.example.units.HelloUnit;
import org.jbpm.example.units.HelloVariableUnit;
import org.jbpm.example.units.PausingUnit;
import org.jbpm.units.ProcessUnitInstance;
import org.jbpm.units.ProcessUnitSubsystem;
import org.junit.Test;
import org.kie.api.DynamicUnitSupport;
import org.kie.api.KieUnitExecutor;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.process.WorkItem;
import org.kie.api.runtime.process.WorkItemHandler;
import org.kie.api.runtime.process.WorkItemManager;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.kie.api.UnitInstance.State.Completed;
import static org.kie.api.UnitInstance.State.Created;
import static org.kie.api.UnitInstance.State.Entering;
import static org.kie.api.UnitInstance.State.Exiting;
import static org.kie.api.UnitInstance.State.Faulted;
import static org.kie.api.UnitInstance.State.ReEntering;
import static org.kie.api.UnitInstance.State.Suspended;

public class ProcessUnitTests {

    @Test
    public void lifecycleShouldMatch() {
        HelloUnit u = new HelloUnit();
        KieUnitExecutor.create(
                DynamicUnitSupport.register(
                        ProcessUnitSubsystem::new)).run(u);
        assertEquals(asList(Created, Entering, Exiting, Completed),
                     u.stateSequence);
    }

    @Test
    public void failedLifecycleShouldMatch() {
        FailingUnit u = new FailingUnit();
        KieUnitExecutor.create(
                DynamicUnitSupport.register(
                        ProcessUnitSubsystem::new)).run(u);
        assertEquals(asList(Created, Entering, Faulted, Completed),
                     u.stateSequence);
    }

    @Test
    public void resultShouldBeSetToPojo() {
        HelloVariableUnit u = new HelloVariableUnit(10);

        KieUnitExecutor
                .create(ProcessUnitSubsystem::new)
                .run(u);

        assertEquals(u.getCount(), 100);
    }

    @Test
    public void pausedLifecycleShouldMatch() {
        PausingUnit u = new PausingUnit();
        KieUnitExecutor executor = KieUnitExecutor.create(
                DynamicUnitSupport.register(
                        ProcessUnitSubsystem::new));

        KieSession session = executor.getSession();

        MyWorkItemHandler handler = new MyWorkItemHandler();
        WorkItemManager mgr = session.getWorkItemManager();
        mgr.registerWorkItemHandler("Human Task", handler);

        ProcessUnitInstance instance = (ProcessUnitInstance) executor.run(u);
        assertEquals(asList(Created, Entering, Suspended),
                     u.stateSequence);

        u.stateSequence.clear();

        mgr.completeWorkItem(handler.workItem.getId(), Collections.emptyMap());

        executor.run(instance);
        assertEquals(asList(ReEntering, Exiting, Completed),
                     u.stateSequence);
    }

    private static class MyWorkItemHandler implements WorkItemHandler {

        WorkItem workItem;

        @Override
        public void executeWorkItem(WorkItem workItem, WorkItemManager manager) {
            System.out.println("execute work item");
            this.workItem = workItem;
        }

        @Override
        public void abortWorkItem(WorkItem workItem, WorkItemManager manager) {
            System.out.println("abort work item");
        }
    }
}
