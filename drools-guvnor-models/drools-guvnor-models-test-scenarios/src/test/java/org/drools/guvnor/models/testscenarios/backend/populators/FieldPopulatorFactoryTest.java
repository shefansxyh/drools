/*
 * Copyright 2012 JBoss Inc
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

package org.drools.guvnor.models.testscenarios.backend.populators;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.drools.core.base.TypeResolver;
import org.drools.guvnor.models.testscenarios.shared.Field;
import org.junit.Before;
import org.junit.Test;
import org.drools.guvnor.models.testscenarios.backend.Cheese;
import org.drools.guvnor.models.testscenarios.shared.Fact;
import org.drools.guvnor.models.testscenarios.shared.FactAssignmentField;
import org.mockito.Matchers;

public class FieldPopulatorFactoryTest {

    private FieldPopulatorFactory fieldPopulatorFactory;
    private TypeResolver typeResolver;
    private ClassLoader classLoader;

    @Before
    public void setUp() throws Exception {
        Object factObject = mock(Object.class);
        typeResolver = mock(TypeResolver.class);
        classLoader = Thread.currentThread().getContextClassLoader();
        fieldPopulatorFactory = new FieldPopulatorFactory(factObject, typeResolver, classLoader);
    }

    @Test
    public void testFactAssignmentField() throws Exception {

        FactAssignmentField field = new FactAssignmentField();
        field.setFact(new Fact());


        when(
                typeResolver.resolveType(Matchers.<String>any())
        ).thenReturn(
                Cheese.class
        );

        FieldPopulator fieldPopulator = fieldPopulatorFactory.getFieldPopulator(field);

        assertNotNull(fieldPopulator);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUnknownFieldType() throws Exception {
        fieldPopulatorFactory.getFieldPopulator(new WhoAmIField());
    }

    class WhoAmIField implements Field {

        @Override
        public String getName() {
            return "?";
        }
    }
}
