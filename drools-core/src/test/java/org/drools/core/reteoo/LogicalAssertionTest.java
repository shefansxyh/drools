/*
 * Copyright 2005 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.drools.core.reteoo;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import org.drools.core.Agenda;
import org.drools.core.FactException;
import org.drools.core.RuleBaseConfiguration;
import org.drools.core.RuleBaseFactory;
import org.drools.core.FactHandle;
import org.drools.core.WorkingMemory;
import org.drools.core.base.ClassObjectType;
import org.drools.core.base.DefaultKnowledgeHelper;
import org.drools.core.beliefsystem.BeliefSet;
import org.drools.core.common.DefaultFactHandle;
import org.drools.core.common.EqualityKey;
import org.drools.core.common.InternalAgenda;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.LogicalDependency;
import org.drools.core.common.NamedEntryPoint;
import org.drools.core.common.PropagationContextImpl;
import org.drools.core.common.SimpleLogicalDependency;
import org.drools.core.common.TruthMaintenanceSystem;
import org.drools.core.test.model.Cheese;
import org.drools.core.test.model.DroolsTestCase;
import org.drools.core.util.Iterator;
import org.drools.core.util.LinkedList;
import org.drools.core.util.LinkedListEntry;
import org.drools.core.util.ObjectHashMap;
import org.drools.core.util.ObjectHashMap.ObjectEntry;
import org.drools.core.reteoo.ReteooBuilder.IdGenerator;
import org.drools.core.reteoo.builder.BuildContext;
import org.drools.core.rule.EntryPoint;
import org.drools.core.rule.Rule;
import org.drools.core.spi.Activation;
import org.drools.core.spi.Consequence;
import org.drools.core.spi.KnowledgeHelper;
import org.drools.core.spi.PropagationContext;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class LogicalAssertionTest extends DroolsTestCase {
    private ReteooRuleBase ruleBase;
    private BuildContext   buildContext;
    private EntryPointNode entryPoint;

    @Before
    public void setUp() throws Exception {
        ruleBase = (ReteooRuleBase) RuleBaseFactory.newRuleBase();
        buildContext = new BuildContext( ruleBase,
                                         ((ReteooRuleBase) ruleBase).getReteooBuilder().getIdGenerator() );
        this.entryPoint = new EntryPointNode( 0,
                                              this.ruleBase.getRete(),
                                              buildContext );
        this.entryPoint.attach( buildContext );
    }

    @Test
    public void testSingleLogicalRelationship() throws Exception {
        IdGenerator idGenerator = ruleBase.getReteooBuilder().getIdGenerator();

        final Rete rete = ruleBase.getRete();
        // create a RuleBase with a single ObjectTypeNode we attach a
        // MockObjectSink so we can detect assertions and retractions
        final ObjectTypeNode objectTypeNode = new ObjectTypeNode( idGenerator.getNextId(),
                                                                  this.entryPoint,
                                                                  new ClassObjectType( String.class ),
                                                                  buildContext );

        MockRightTupleSink sink = new MockRightTupleSink();

        final Rule rule1 = new Rule( "test-rule1" );
        final RuleTerminalNode node = new RuleTerminalNode( idGenerator.getNextId(),
                                                            new MockTupleSource( idGenerator.getNextId() ),
                                                            rule1,
                                                            rule1.getLhs(),
                                                            0,
                                                            buildContext );
        final ReteooWorkingMemory workingMemory = (ReteooWorkingMemory) ruleBase.newStatefulSession();

        final InternalAgenda agenda = (InternalAgenda) workingMemory.getAgenda();

        final Consequence consequence = new Consequence() {
            private static final long serialVersionUID = 510l;

            public void evaluate(KnowledgeHelper knowledgeHelper,
                                 WorkingMemory workingMemory) {
                LinkedList< LogicalDependency > list = ((DefaultKnowledgeHelper)knowledgeHelper).getpreviousJustified();
                if ( list != null ) {
                    for ( SimpleLogicalDependency dep = ( SimpleLogicalDependency ) list.getFirst(); dep != null; dep =  ( SimpleLogicalDependency ) dep.getNext() ){
                        knowledgeHelper.insertLogical( dep.getObject(), dep.getValue() );
                    }
                }
            }

            public void readExternal(ObjectInput in) throws IOException,
                                                    ClassNotFoundException {

            }

            public void writeExternal(ObjectOutput out) throws IOException {

            }

            public String getName() {
                return "default";
            }
        };

        rule1.setConsequence( consequence );

        final DefaultFactHandle handle1 = (DefaultFactHandle) workingMemory.insert( "o1" );
        final LeftTupleImpl tuple1 = new LeftTupleImpl( handle1,
                                                        null,
                                                        true );

        final PropagationContext context1 = new PropagationContextImpl( 0,
                                                                        PropagationContext.INSERTION,
                                                                        null,
                                                                        null,
                                                                        new DefaultFactHandle()  );

        // Test single activation for a single logical assertions
        node.assertLeftTuple( tuple1,
                              context1,
                              workingMemory );

        final String logicalString = new String( "logical" );
        InternalFactHandle logicalHandle = (InternalFactHandle) workingMemory.insert( logicalString,
                                                                                      null,
                                                                                      false,
                                                                                      true,
                                                                                      rule1,
                                                                                      (Activation) tuple1.getObject() );
        new RightTuple( logicalHandle,
                        sink );
        context1.setFactHandle( handle1 );
        // Retract the tuple and test the logically asserted fact was also retracted
        node.retractLeftTuple( tuple1,
                               context1,
                               workingMemory );

        workingMemory.executeQueuedActions();

        assertLength( 1,
                      sink.getRetracted() );

        Object[] values = (Object[]) sink.getRetracted().get( 0 );

        assertSame( logicalHandle,
                    ((RightTuple) values[0]).getFactHandle() );

        // Test single activation for a single logical assertions. This also
        // tests that logical assertions live on after the related Activation
        // has fired.
        node.assertLeftTuple( tuple1,
                              context1,
                              workingMemory );
        logicalHandle = (InternalFactHandle) workingMemory.insert( logicalString,
                                                                   null,
                                                                   false,
                                                                   true,
                                                                   rule1,
                                                                   (Activation) tuple1.getObject() );

        new RightTuple( logicalHandle,
                        sink );

        agenda.fireNextItem( null, 0, -1 );

        node.retractLeftTuple( tuple1,
                               context1,
                               workingMemory );

        workingMemory.executeQueuedActions();

        assertLength( 2,
                      sink.getRetracted() );

        values = (Object[]) sink.getRetracted().get( 1 );

        assertSame( logicalHandle,
                    ((RightTuple) values[0]).getFactHandle() );
    }

    @Test
    public void testEqualsMap() throws Exception {
        // create a RuleBase with a single ObjectTypeNode we attach a
        // MockObjectSink so w can detect assertions and retractions
        final Rule rule1 = new Rule( "test-rule1" );

        IdGenerator idGenerator = ruleBase.getReteooBuilder().getIdGenerator();

        final Rete rete = ruleBase.getRete();
        final ObjectTypeNode objectTypeNode = new ObjectTypeNode( idGenerator.getNextId(),
                                                                  this.entryPoint,
                                                                  new ClassObjectType( String.class ),
                                                                  buildContext );
        objectTypeNode.attach( buildContext );
        final MockObjectSink sink = new MockObjectSink();
        objectTypeNode.addObjectSink( sink );

        final RuleTerminalNode node = new RuleTerminalNode( idGenerator.getNextId(),
                                                            new MockTupleSource( idGenerator.getNextId() ),
                                                            rule1,
                                                            rule1.getLhs(),
                                                            0,
                                                            buildContext );

        final ReteooWorkingMemory workingMemory = (ReteooWorkingMemory) ruleBase.newStatefulSession();

        final Agenda agenda = workingMemory.getAgenda();

        final Consequence consequence = new Consequence() {
            private static final long serialVersionUID = 510l;

            public void evaluate(KnowledgeHelper knowledgeHelper,
                                 WorkingMemory workingMemory) {
                LinkedList< LogicalDependency > list = ((DefaultKnowledgeHelper)knowledgeHelper).getpreviousJustified();
                if ( list != null ) {
                    for ( SimpleLogicalDependency dep = ( SimpleLogicalDependency ) list.getFirst(); dep != null; dep =  ( SimpleLogicalDependency ) dep.getNext() ){
                        knowledgeHelper.insertLogical( dep.getObject(), dep.getValue() );
                    }
                }
            }

            public void readExternal(ObjectInput in) throws IOException,
                                                    ClassNotFoundException {

            }

            public void writeExternal(ObjectOutput out) throws IOException {

            }

            public String getName() {
                return "default";
            }
        };
        rule1.setConsequence( consequence );

        final DefaultFactHandle handle1 = new DefaultFactHandle( 1,
                                                                 "cheese" );
        final LeftTupleImpl tuple1 = new LeftTupleImpl( handle1,
                                                        null,
                                                        true );

        final PropagationContext context1 = new PropagationContextImpl( 0,
                                                                        PropagationContext.INSERTION,
                                                                        null,
                                                                        null,
                                                                        new DefaultFactHandle()  );

        // Test single activation for a single logical assertions
        node.assertLeftTuple( tuple1,
                              context1,
                              workingMemory );

        final String logicalString1 = new String( "logical" );
        FactHandle logicalHandle1 = workingMemory.insert( logicalString1,
                                                          null,
                                                          false,
                                                          true,
                                                          rule1,
                                                          (Activation) tuple1.getObject() );

        final String logicalString2 = new String( "logical" );
        FactHandle logicalHandle2 = workingMemory.insert( logicalString2,
                                                          null,
                                                          false,
                                                          true,
                                                          rule1,
                                                          (Activation) tuple1.getObject() );

        assertSame( logicalHandle1,
                    logicalHandle2 );

        // little sanity check using normal assert
        logicalHandle1 = workingMemory.insert( logicalString1 );
        logicalHandle2 = workingMemory.insert( logicalString2 );

        // If assert behavior in working memory is IDENTITY,
        // returned handles must not be the same
        if ( RuleBaseConfiguration.AssertBehaviour.IDENTITY.equals( ((ReteooRuleBase) ruleBase).getConfiguration().getAssertBehaviour() ) ) {

            assertNotSame( logicalHandle1,
                           logicalHandle2 );
        } else {
            // in case behavior is EQUALS, handles should be the same
            assertSame( logicalHandle1,
                        logicalHandle2 );
        }
    }

    /**
     * This tests that Stated asserts always take precedent
     *
     * @throws Exception
     */
    @Test
    public void testStatedOverride() throws Exception {
        // create a RuleBase with a single ObjectTypeNode we attach a
        // MockObjectSink so we can detect assertions and retractions
        final Rule rule1 = new Rule( "test-rule1" );
        IdGenerator idGenerator = ruleBase.getReteooBuilder().getIdGenerator();

        final Rete rete = ruleBase.getRete();
        final ObjectTypeNode objectTypeNode = new ObjectTypeNode( idGenerator.getNextId(),
                                                                  this.entryPoint,
                                                                  new ClassObjectType( String.class ),
                                                                  buildContext );
        objectTypeNode.attach( buildContext );
        final MockObjectSink sink = new MockObjectSink();
        objectTypeNode.addObjectSink( sink );
        final RuleTerminalNode node = new RuleTerminalNode( idGenerator.getNextId(),
                                                            new MockTupleSource( idGenerator.getNextId() ),
                                                            rule1,
                                                            rule1.getLhs(),
                                                            0,
                                                            buildContext );

        final ReteooWorkingMemory workingMemory = (ReteooWorkingMemory) ruleBase.newStatefulSession();

        final Agenda agenda = workingMemory.getAgenda();

        final Consequence consequence = new Consequence() {
            private static final long serialVersionUID = 510l;

            public void evaluate(KnowledgeHelper knowledgeHelper,
                                 WorkingMemory workingMemory) {
                LinkedList< LogicalDependency > list = ((DefaultKnowledgeHelper)knowledgeHelper).getpreviousJustified();
                if ( list != null ) {
                    for ( SimpleLogicalDependency dep = ( SimpleLogicalDependency ) list.getFirst(); dep != null; dep =  ( SimpleLogicalDependency ) dep.getNext() ){
                        knowledgeHelper.insertLogical( dep.getObject(), dep.getValue() );
                    }
                }
            }

            public void readExternal(ObjectInput in) throws IOException,
                                                    ClassNotFoundException {

            }

            public void writeExternal(ObjectOutput out) throws IOException {

            }

            public String getName() {
                return "default";
            }
        };
        rule1.setConsequence( consequence );

        final DefaultFactHandle handle1 = new DefaultFactHandle( 1,
                                                                 "cheese" );
        final LeftTupleImpl tuple1 = new LeftTupleImpl( handle1,
                                                        null,
                                                        true );

        final PropagationContext context1 = new PropagationContextImpl( 0,
                                                                        PropagationContext.INSERTION,
                                                                        null,
                                                                        null,
                                                                        new DefaultFactHandle()  );

        // Test that a STATED assertion overrides a logical assertion
        node.assertLeftTuple( tuple1,
                              context1,
                              workingMemory );

        String logicalString1 = new String( "logical" );
        FactHandle logicalHandle1 = workingMemory.insert( logicalString1,
                                                          null,
                                                          false,
                                                          true,
                                                          rule1,
                                                          (Activation) tuple1.getObject() );

        // This assertion is stated and should override any previous justified
        // "equals" objects.
        String logicalString2 = new String( "logical" );
        FactHandle logicalHandle2 = workingMemory.insert( logicalString2 );

        node.retractLeftTuple( tuple1,
                               context1,
                               workingMemory );

        assertLength( 0,
                      sink.getRetracted() );

        //  we override and discard the original logical object
        assertSame( logicalHandle2,
                    logicalHandle1 );

        // so while new STATED assertion is equal
        assertEquals( logicalString1,
                      workingMemory.getObject( logicalHandle2 ) );
        // they are not identity same
        assertNotSame( logicalString1,
                       workingMemory.getObject( logicalHandle2 ) );

        // Test that a logical assertion cannot override a STATED assertion
        node.assertLeftTuple( tuple1,
                              context1,
                              workingMemory );

        logicalString2 = new String( "logical" );
        logicalHandle2 = workingMemory.insert( logicalString2 );

        // This logical assertion will be ignored as there is already
        // an equals STATED assertion.
        logicalString1 = new String( "logical" );
        logicalHandle1 = workingMemory.insert( logicalString1,
                                               null,
                                               false,
                                               true,
                                               rule1,
                                               (Activation) tuple1.getObject() );

        assertNull( logicalHandle1 );

        // Already identify same so return previously assigned handle
        logicalHandle1 = workingMemory.insert( logicalString2,
                                               null,
                                               false,
                                               false,
                                               rule1,
                                               (Activation) tuple1.getObject() );
        // return the matched handle

        assertSame( logicalHandle2,
                    logicalHandle1 );

        node.retractLeftTuple( tuple1,
                               context1,
                               workingMemory );

        assertLength( 0,
                      sink.getRetracted() );

        // Should keep the same handle when overriding
        assertSame( logicalHandle1,
                    logicalHandle2 );

        // so while new STATED assertion is equal
        assertEquals( logicalString1,
                      workingMemory.getObject( logicalHandle2 ) );

        // they are not identity same
        assertNotSame( logicalString1,
                       workingMemory.getObject( logicalHandle2 ) );

    }

    @Test
    public void testRetract() throws Exception {
        // create a RuleBase with a single ObjectTypeNode we attach a
        // MockObjectSink so we can detect assertions and retractions
        final Rule rule1 = new Rule( "test-rule1" );
        IdGenerator idGenerator = ruleBase.getReteooBuilder().getIdGenerator();

        final Rete rete = ruleBase.getRete();
        final ObjectTypeNode objectTypeNode = new ObjectTypeNode( idGenerator.getNextId(),
                                                                  this.entryPoint,
                                                                  new ClassObjectType( String.class ),
                                                                  buildContext );
        objectTypeNode.attach( buildContext );
        final MockObjectSink sink = new MockObjectSink();
        objectTypeNode.addObjectSink( sink );
        final RuleTerminalNode node = new RuleTerminalNode( idGenerator.getNextId(),
                                                            new MockTupleSource( idGenerator.getNextId() ),
                                                            rule1,
                                                            rule1.getLhs(),
                                                            0,
                                                            buildContext );

        final ReteooWorkingMemory workingMemory = (ReteooWorkingMemory) ruleBase.newStatefulSession();

        final Consequence consequence = new Consequence() {
            private static final long serialVersionUID = 510l;

            public void evaluate(KnowledgeHelper knowledgeHelper,
                                 WorkingMemory workingMemory) {
                LinkedList< LogicalDependency > list = ((DefaultKnowledgeHelper)knowledgeHelper).getpreviousJustified();
                if ( list != null ) {
                    for ( SimpleLogicalDependency dep = ( SimpleLogicalDependency ) list.getFirst(); dep != null; dep =  ( SimpleLogicalDependency ) dep.getNext() ){
                        knowledgeHelper.insertLogical( dep.getObject(), dep.getValue() );
                    }
                }
            }

            public void readExternal(ObjectInput in) throws IOException,
                                                    ClassNotFoundException {

            }

            public void writeExternal(ObjectOutput out) throws IOException {

            }

            public String getName() {
                return "default";
            }
        };

        // create the first activation which will justify the fact "logical"
        rule1.setConsequence( consequence );

        final DefaultFactHandle handle1 = new DefaultFactHandle( 1,
                                                                 "cheese" );
        final LeftTupleImpl tuple1 = new LeftTupleImpl( handle1,
                                                        null,
                                                        true );

        final PropagationContext context = new PropagationContextImpl( 0,
                                                                       PropagationContext.INSERTION,
                                                                       null,
                                                                       null,
                                                                       new DefaultFactHandle()  );

        node.assertLeftTuple( tuple1,
                              context,
                              workingMemory );

        // Assert the logical "logical" fact
        final String logicalString1 = new String( "logical" );
        final FactHandle logicalHandle1 = workingMemory.insert( logicalString1,
                                                                null,
                                                                false,
                                                                true,
                                                                rule1,
                                                                (Activation) tuple1.getObject() );

        // create the second activation to justify the "logical" fact
        final Rule rule2 = new Rule( "test-rule2" );
        final RuleTerminalNode node2 = new RuleTerminalNode( idGenerator.getNextId(),
                                                             new MockTupleSource( 3 ),
                                                             rule2,
                                                             rule2.getLhs(),
                                                             0,
                                                             buildContext );
        rule2.setConsequence( consequence );

        final DefaultFactHandle handle2 = new DefaultFactHandle( 2,
                                                                 "cheese" );
        final LeftTupleImpl tuple2 = new LeftTupleImpl( handle2,
                                                        null,
                                                        true );

        node.assertLeftTuple( tuple2,
                              context,
                              workingMemory );

        node2.assertLeftTuple( tuple2,
                               context,
                               workingMemory );

        // Assert the logical "logical" fact
        final String logicalString2 = new String( "logical" );
        final FactHandle logicalHandle2 = workingMemory.insert( logicalString2,
                                                                null,
                                                                false,
                                                                true,
                                                                rule2,
                                                                (Activation) tuple2.getObject() );

        assertSame( logicalHandle1, logicalHandle2 );
        
        TruthMaintenanceSystem tms = ((NamedEntryPoint)workingMemory.getWorkingMemoryEntryPoint( EntryPoint.DEFAULT.getEntryPointId() ) ).getTruthMaintenanceSystem();
        
        // "logical" should only appear once
        assertEquals( 1,
                      getLogicalCount( tms ) );

        // retract the logical prime handle
        workingMemory.retract( logicalHandle1 );
;

        // The logical object should now disappear appear
        assertEquals( 0,
                      getLogicalCount( tms ) );
    }

    @Test
    public void testMultipleLogicalRelationships() throws FactException {
        final Rule rule1 = new Rule( "test-rule1" );
        IdGenerator idGenerator = ruleBase.getReteooBuilder().getIdGenerator();

        final Rete rete = ruleBase.getRete();

        // Create a RuleBase with a single ObjectTypeNode we attach a
        // MockObjectSink so we can detect assertions and retractions
        final ObjectTypeNode objectTypeNode = new ObjectTypeNode( idGenerator.getNextId(),
                                                                  this.entryPoint,
                                                                  new ClassObjectType( String.class ),
                                                                  buildContext );
        objectTypeNode.attach( buildContext );

        MockRightTupleSink sink = new MockRightTupleSink();

        final RuleTerminalNode node = new RuleTerminalNode( idGenerator.getNextId(),
                                                            new MockTupleSource( idGenerator.getNextId() ),
                                                            rule1,
                                                            rule1.getLhs(),
                                                            0,
                                                            buildContext );
        final ReteooWorkingMemory workingMemory = (ReteooWorkingMemory) ruleBase.newStatefulSession();

        final Agenda agenda = workingMemory.getAgenda();

        final Consequence consequence = new Consequence() {
            private static final long serialVersionUID = 510l;

            public void evaluate(KnowledgeHelper knowledgeHelper,
                                 WorkingMemory workingMemory) {
                LinkedList< LogicalDependency > list = ((DefaultKnowledgeHelper)knowledgeHelper).getpreviousJustified();
                if ( list != null ) {
                    for ( SimpleLogicalDependency dep = ( SimpleLogicalDependency ) list.getFirst(); dep != null; dep =  ( SimpleLogicalDependency ) dep.getNext() ){
                        knowledgeHelper.insertLogical( dep.getObject(), dep.getValue() );
                    }
                }
            }

            public void readExternal(ObjectInput in) throws IOException,
                                                    ClassNotFoundException {

            }

            public void writeExternal(ObjectOutput out) throws IOException {

            }

            public String getName() {
                return "default";
            }
        };

        // Create first justifier
        rule1.setConsequence( consequence );

        final DefaultFactHandle handle1 = new DefaultFactHandle( 1,
                                                                 "cheese" );
        final LeftTupleImpl tuple1 = new LeftTupleImpl( handle1,
                                                        null,
                                                        true );

        final PropagationContext context1 = new PropagationContextImpl( 0,
                                                                        PropagationContext.INSERTION,
                                                                        null,
                                                                        null,
                                                                        new DefaultFactHandle()  );
        // get the activation onto the agenda
        node.assertLeftTuple( tuple1,
                              context1,
                              workingMemory );

        // Create the second justifier
        final Rule rule2 = new Rule( "test-rule2" );
        final RuleTerminalNode node2 = new RuleTerminalNode( idGenerator.getNextId(),
                                                             new MockTupleSource( idGenerator.getNextId() ),
                                                             rule2,
                                                             rule2.getLhs(),
                                                             0,
                                                             buildContext );
        rule2.setConsequence( consequence );

        final DefaultFactHandle handle2 = new DefaultFactHandle( 2,
                                                                 "cheese" );
        final LeftTupleImpl tuple2 = new LeftTupleImpl( handle2,
                                                        null,
                                                        true );

        final PropagationContext context2 = new PropagationContextImpl( 0,
                                                                        PropagationContext.INSERTION,
                                                                        null,
                                                                        null,
                                                                        new DefaultFactHandle()  );

        // get the activations onto the agenda
        node2.assertLeftTuple( tuple2,
                               context2,
                               workingMemory );

        // Create the first justifieable relationship
        final String logicalString1 = new String( "logical" );
        final InternalFactHandle logicalHandle1 = (InternalFactHandle) workingMemory.insert( logicalString1,
                                                                                             "value1",
                                                                                             false,
                                                                                             true,
                                                                                             rule1,
                                                                                             (Activation) tuple1.getObject() );
        new RightTuple( logicalHandle1,
                        sink );

        // Create the second justifieable relationship
        final String logicalString2 = new String( "logical" );
        final InternalFactHandle logicalHandle2 = (InternalFactHandle) workingMemory.insert( logicalString2,
                                                                                             "value2",
                                                                                             false,
                                                                                             true,
                                                                                             rule2,
                                                                                             (Activation) tuple2.getObject() );

        assertSame( logicalHandle1, logicalHandle2 );

        TruthMaintenanceSystem tms = ((NamedEntryPoint)workingMemory.getWorkingMemoryEntryPoint( EntryPoint.DEFAULT.getEntryPointId() ) ).getTruthMaintenanceSystem();
        
        // "logical" should only appear once
        assertEquals( 1,
                      getLogicalCount( tms ) );
        
        BeliefSet bs =  ( BeliefSet ) logicalHandle2.getEqualityKey().getBeliefSet();       
        assertEquals( "value1", ((LogicalDependency) ((LinkedListEntry)bs.getFirst()).getObject()).getValue() );
        assertEquals( "value2", ((LogicalDependency) ((LinkedListEntry)bs.getFirst().getNext()).getObject()).getValue() );

        // Now lets cancel the first activation
        node2.retractLeftTuple( tuple2,
                                context2,
                                workingMemory );

        workingMemory.executeQueuedActions();

        // because this logical fact has two relationships it shouldn't retract yet
        assertLength( 0,
                      sink.getRetracted() );

        // check "logical" is still in the system
        assertEquals( 1,
                      getLogicalCount( ( tms ) ) );

        // now remove that final justification
        node.retractLeftTuple( tuple1,
                               context1,
                               workingMemory );

        workingMemory.executeQueuedActions();

        // Should cause the logical fact to be retracted
        assertLength( 1,
                      sink.getRetracted() );

        // "logical" fact should no longer be in the system
        assertEquals( 0,
                      getLogicalCount( tms ) );
    }

    /**
     * This tests that when multiple not identical, but equals facts, are asserted
     * into WM, only when all are removed, a logical assert will succeed
     *
     * @throws Exception
     */
    @Test
    public void testMultipleAssert() throws Exception {
        // create a RuleBase with a single ObjectTypeNode we attach a
        // MockObjectSink so we can detect assertions and retractions
        final Rule rule1 = new Rule( "test-rule1" );
        IdGenerator idGenerator = ruleBase.getReteooBuilder().getIdGenerator();

        final Rete rete = ruleBase.getRete();
        final ObjectTypeNode objectTypeNode = new ObjectTypeNode( idGenerator.getNextId(),
                                                                  this.entryPoint,
                                                                  new ClassObjectType( String.class ),
                                                                  buildContext );
        objectTypeNode.attach( buildContext );
        final MockObjectSink sink = new MockObjectSink();
        objectTypeNode.addObjectSink( sink );
        final RuleTerminalNode node = new RuleTerminalNode( idGenerator.getNextId(),
                                                            new MockTupleSource( idGenerator.getNextId() ),
                                                            rule1,
                                                            rule1.getLhs(),
                                                            0,
                                                            buildContext );

        final ReteooWorkingMemory workingMemory = (ReteooWorkingMemory) ruleBase.newStatefulSession();

        final Agenda agenda = workingMemory.getAgenda();

        final Consequence consequence = new Consequence() {
            private static final long serialVersionUID = 510l;

            public void evaluate(KnowledgeHelper knowledgeHelper,
                                 WorkingMemory workingMemory) {
                LinkedList< LogicalDependency > list = ((DefaultKnowledgeHelper)knowledgeHelper).getpreviousJustified();
                if ( list != null ) {
                    for ( SimpleLogicalDependency dep = ( SimpleLogicalDependency ) list.getFirst(); dep != null; dep =  ( SimpleLogicalDependency ) dep.getNext() ){
                        knowledgeHelper.insertLogical( dep.getObject(), dep.getValue() );
                    }
                }
            }

            public void readExternal(ObjectInput in) throws IOException,
                                                    ClassNotFoundException {

            }

            public void writeExternal(ObjectOutput out) throws IOException {

            }

            public String getName() {
                return "default";
            }
        };
        rule1.setConsequence( consequence );

        final DefaultFactHandle handle1 = new DefaultFactHandle( 1,
                                                                 "cheese" );
        final LeftTupleImpl tuple1 = new LeftTupleImpl( handle1,
                                                        null,
                                                        true );

        final PropagationContext context1 = new PropagationContextImpl( 0,
                                                                        PropagationContext.INSERTION,
                                                                        null,
                                                                        null,
                                                                        new DefaultFactHandle()  );

        // Assert multiple stated objects
        node.assertLeftTuple( tuple1,
                              context1,
                              workingMemory );

        final String statedString1 = new String( "logical" );
        final FactHandle statedHandle1 = workingMemory.insert( statedString1 );

        final String statedString2 = new String( "logical" );
        final FactHandle statedHandle2 = workingMemory.insert( statedString2 );

        // This assertion is logical should fail as there is previous stated objects
        final String logicalString3 = new String( "logical" );
        FactHandle logicalHandle3 = workingMemory.insert( logicalString3,
                                                          null,
                                                          false,
                                                          true,
                                                          rule1,
                                                          (Activation) tuple1.getObject() );

        // Checks that previous LogicalAssert failed
        assertNull( logicalHandle3 );

        // If assert behavior in working memory is IDENTITY,
        // we need to retract object 2 times before being able to
        // succesfully logically assert a new fact
        if ( RuleBaseConfiguration.AssertBehaviour.IDENTITY.equals( ((ReteooRuleBase) ruleBase).getConfiguration().getAssertBehaviour() ) ) {

            workingMemory.retract( statedHandle2 );

            logicalHandle3 = workingMemory.insert( logicalString3,
                                                   null,
                                                   false,
                                                   true,
                                                   rule1,
                                                   (Activation) tuple1.getObject() );

            // Checks that previous LogicalAssert failed
            assertNull( logicalHandle3 );
        }

        workingMemory.retract( statedHandle1 );

        logicalHandle3 = workingMemory.insert( logicalString3,
                                               null,
                                               false,
                                               true,
                                               rule1,
                                               (Activation) tuple1.getObject() );

        // Checks that previous LogicalAssert succeeded as there are no more
        // stated strings in the working memory
        assertNotNull( logicalHandle3 );

    }

    /**
     * This test checks that truth maintenance is correctly maintained for modified objects
     */
    @Test
    public void testMutableObject() {
        // create a RuleBase with a single ObjectTypeNode we attach a
        // MockObjectSink so we can detect assertions and retractions
        final Rule rule1 = new Rule( "test-rule1" );
        IdGenerator idGenerator = ruleBase.getReteooBuilder().getIdGenerator();

        final Rete rete = ruleBase.getRete();
        final ObjectTypeNode objectTypeNode = new ObjectTypeNode( idGenerator.getNextId(),
                                                                  this.entryPoint,
                                                                  new ClassObjectType( String.class ),
                                                                  buildContext );
        objectTypeNode.attach( buildContext );
        final MockObjectSink sink = new MockObjectSink();
        objectTypeNode.addObjectSink( sink );
        final RuleTerminalNode node = new RuleTerminalNode( idGenerator.getNextId(),
                                                            new MockTupleSource( idGenerator.getNextId() ),
                                                            rule1,
                                                            rule1.getLhs(),
                                                            0,
                                                            buildContext );
        final ReteooWorkingMemory workingMemory = (ReteooWorkingMemory) ruleBase.newStatefulSession();

        final Agenda agenda = workingMemory.getAgenda();

        final Consequence consequence = new Consequence() {
            private static final long serialVersionUID = 510l;

            public void evaluate(KnowledgeHelper knowledgeHelper,
                                 WorkingMemory workingMemory) {
                LinkedList< LogicalDependency > list = ((DefaultKnowledgeHelper)knowledgeHelper).getpreviousJustified();
                if ( list != null ) {
                    for ( SimpleLogicalDependency dep = ( SimpleLogicalDependency ) list.getFirst(); dep != null; dep =  ( SimpleLogicalDependency ) dep.getNext() ){
                        knowledgeHelper.insertLogical( dep.getObject(), dep.getValue() );
                    }
                }
            }

            public void readExternal(ObjectInput in) throws IOException,
                                                    ClassNotFoundException {

            }

            public void writeExternal(ObjectOutput out) throws IOException {

            }

            public String getName() {
                return "default";
            }
        };
        rule1.setConsequence( consequence );

        final DefaultFactHandle handle1 = new DefaultFactHandle( 1,
                                                                 "cheese" );
        final LeftTupleImpl tuple1 = new LeftTupleImpl( handle1,
                                                        null,
                                                        true );

        final PropagationContext context1 = new PropagationContextImpl( 0,
                                                                        PropagationContext.INSERTION,
                                                                        null,
                                                                        null,
                                                                        new DefaultFactHandle()  );

        // Test that a STATED assertion overrides a logical assertion
        node.assertLeftTuple( tuple1,
                              context1,
                              workingMemory );

        final Cheese cheese = new Cheese( "brie",
                                          10 );
        final FactHandle cheeseHandle = workingMemory.insert( cheese,
                                                              null,
                                                              false,
                                                              true,
                                                              rule1,
                                                              (Activation) tuple1.getObject() );

        cheese.setType( "cheddar" );
        cheese.setPrice( 20 );
        TruthMaintenanceSystem tms = ((NamedEntryPoint)workingMemory.getWorkingMemoryEntryPoint( EntryPoint.DEFAULT.getEntryPointId() ) ).getTruthMaintenanceSystem();
        
        assertEquals( 1,
                      getLogicalCount( tms ) );
        assertEquals( 1,
                      tms.getEqualityKeyMap().size() );

        workingMemory.retract( cheeseHandle );

        assertEquals( 0,
                      getLogicalCount( tms ) );
        assertEquals( 0,
                      tms.getEqualityKeyMap().size() );
    }
    
    public int getLogicalCount(TruthMaintenanceSystem tms) {
        ObjectHashMap map = tms.getEqualityKeyMap();
        final Iterator<ObjectEntry> it = map.iterator();
        int i = 0;
        for ( ObjectEntry entry =  it.next(); entry != null; entry = it.next() ) {
            EqualityKey key = (EqualityKey) entry.getKey();
            if ( key.getStatus() == EqualityKey.JUSTIFIED ) {
                i++;
            }
        }
        return i;
    }
}
