/*
 * Copyright (c) 2002-2018 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.cypher.internal.runtime.interpreted.pipes

import org.neo4j.cypher.internal.runtime.QueryContext
import org.neo4j.cypher.internal.runtime.interpreted.ExecutionContext
import org.neo4j.cypher.internal.runtime.interpreted.commands.expressions.Expression
import org.neo4j.cypher.internal.runtime.interpreted.commands.indexQuery
import org.neo4j.cypher.internal.v3_4.expressions.{LabelToken, PropertyKeyToken}
import org.neo4j.cypher.internal.v3_4.logical.plans.{LogicalPlanId, QueryExpression}
import org.neo4j.internal.kernel.api.{CapableIndexReference, IndexReference}

case class NodeIndexSeekPipe(ident: String,
                             label: LabelToken,
                             propertyKeys: Seq[PropertyKeyToken],
                             valueExpr: QueryExpression[Expression],
                             indexMode: IndexSeekMode = IndexSeek)
                            (val id: LogicalPlanId = LogicalPlanId.DEFAULT) extends Pipe {

  private val propertyIds: Array[Int] = propertyKeys.map(_.nameId.id).toArray

  private var reference: IndexReference = CapableIndexReference.NO_INDEX

  private def reference(context: QueryContext): IndexReference = {
    if (reference == CapableIndexReference.NO_INDEX) {
      reference = context.indexReference(label.nameId.id, propertyIds:_*)
    }
    reference
  }

  private def indexFactory(context: QueryContext) = indexMode.indexFactory(reference(context))

  valueExpr.expressions.foreach(_.registerOwningPipe(this))

  protected def internalCreateResults(state: QueryState): Iterator[ExecutionContext] = {
    val index = indexFactory(state.query)(state)
    val baseContext = state.createOrGetInitialContext(executionContextFactory)
    val resultNodes = indexQuery(valueExpr, baseContext, state, index, label.name, propertyKeys.map(_.name))
    resultNodes.map(node => executionContextFactory.copyWith(baseContext, ident, node))
  }

}
