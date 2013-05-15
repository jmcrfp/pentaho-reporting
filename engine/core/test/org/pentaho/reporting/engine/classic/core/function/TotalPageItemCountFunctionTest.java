/*
 * This program is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License, version 2.1 as published by the Free Software
 * Foundation.
 *
 * You should have received a copy of the GNU Lesser General Public License along with this
 * program; if not, you can obtain a copy at http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
 * or from the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * Copyright (c) 2005-2011 Pentaho Corporation.  All rights reserved.
 */

package org.pentaho.reporting.engine.classic.core.function;

import java.awt.Color;
import javax.swing.table.TableModel;

import junit.framework.AssertionFailedError;
import junit.framework.TestCase;
import org.pentaho.reporting.engine.classic.core.AttributeNames;
import org.pentaho.reporting.engine.classic.core.ClassicEngineBoot;
import org.pentaho.reporting.engine.classic.core.MasterReport;
import org.pentaho.reporting.engine.classic.core.SimplePageDefinition;
import org.pentaho.reporting.engine.classic.core.TableDataFactory;
import org.pentaho.reporting.engine.classic.core.designtime.DesignTimeDataSchemaModel;
import org.pentaho.reporting.engine.classic.core.filter.types.TextFieldType;
import org.pentaho.reporting.engine.classic.core.filter.types.bands.GroupFooterType;
import org.pentaho.reporting.engine.classic.core.filter.types.bands.GroupHeaderType;
import org.pentaho.reporting.engine.classic.core.filter.types.bands.ItemBandType;
import org.pentaho.reporting.engine.classic.core.layout.ModelPrinter;
import org.pentaho.reporting.engine.classic.core.layout.model.LogicalPageBox;
import org.pentaho.reporting.engine.classic.core.layout.model.RenderNode;
import org.pentaho.reporting.engine.classic.core.testsupport.DebugReportRunner;
import org.pentaho.reporting.engine.classic.core.testsupport.RelationalReportBuilder;
import org.pentaho.reporting.engine.classic.core.testsupport.selector.AndMatcher;
import org.pentaho.reporting.engine.classic.core.testsupport.selector.AttributeMatcher;
import org.pentaho.reporting.engine.classic.core.testsupport.selector.ElementTypeMatcher;
import org.pentaho.reporting.engine.classic.core.testsupport.selector.MatchFactory;
import org.pentaho.reporting.engine.classic.core.util.PageSize;
import org.pentaho.reporting.engine.classic.core.util.TypedTableModel;
import org.pentaho.reporting.libraries.base.util.DebugLog;


/**
 * @noinspection HardCodedStringLiteral
 */
public class TotalPageItemCountFunctionTest extends TestCase
{

  public static final String ROW_DIMENSION_A = "Row-Dimension-A";
  public static final String ROW_DIMENSION_B = "Row-Dimension-B";
  public static final String COLUMN_DIMENSION_A = "Column-Dimension-A";
  public static final String COLUMN_DIMENSION_B = "Column-Dimension-B";
  public static final String VALUE = "Value";

  public static final Color VALUE_BACKGROUND = new Color(178, 178, 255);
  public static final Color ROWA_BACKGROUND = new Color(255, 178, 255);
  public static final Color ROWA_VALIDATE_BACKGROUND = new Color(255, 208, 255);
  public static final Color ROWB_BACKGROUND = new Color(178, 255, 255);
  public static final Color ROWB_VALIDATE_BACKGROUND = new Color(208, 255, 255);

  public TotalPageItemCountFunctionTest()
  {
  }

  protected void setUp() throws Exception
  {
    ClassicEngineBoot.getInstance().start();
  }

  private TableModel createRelationalTableModel()
  {
    final TypedTableModel model = new TypedTableModel();
    model.addColumn(ROW_DIMENSION_A, String.class);
    model.addColumn(ROW_DIMENSION_B, String.class);
    model.addColumn(VALUE, String.class);
    model.addColumn("validate-row-b-sum", Integer.class);
    model.addColumn("validate-row-a-sum", Integer.class);

    model.addRow("RA", "r1", 1, 1, 5);
    model.addRow("RA", "r2", 2, 1, 5);
    model.addRow("RA", "r1", 1, 3, 5);
    model.addRow("RA", "r1", 1, 3, 5);
    model.addRow("RA", "r1", 2, 3, 5);   // page break
    model.addRow("RA", "r2", 1, 3, 3);
    model.addRow("RA", "r2", 1, 3, 3);
    model.addRow("RA", "r2", 2, 3, 3);
    model.addRow("RB", "r1", 1, 4, 4);
    model.addRow("RB", "r1", 1, 4, 4);
    model.addRow("RB", "r1", 1, 4, 4);
    model.addRow("RB", "r1", 2, 4, 4);  // page break
    model.addRow("RB", "r1", 1, 1, 8);
    model.addRow("RB", "r2", 2, 7, 8);
    model.addRow("RB", "r2", 1, 7, 8);
    model.addRow("RB", "r2", 1, 7, 8);
    model.addRow("RB", "r2", 1, 7, 8);
    model.addRow("RB", "r2", 2, 7, 8);
    model.addRow("RB", "r2", 2, 7, 8);
    model.addRow("RB", "r2", 2, 7, 8);
    return model;
  }

  private AggregationFunction create(final String name,
                                     final String group)
  {
    final TotalPageItemCountFunction detailsSum = new TotalPageItemCountFunction();
    detailsSum.setName(name);
    detailsSum.setGroup(group);
    detailsSum.setDependencyLevel(1);
    return detailsSum;
  }

  /**
   * Disabled for now.  "Page" level functions recalculate values near the page
   * break, which was causing the ValidatePageFunctionResultExpression checks to
   * trigger a fail, even though subsequent computed values were correct.  Need
   * to fine a better way to test Page functions.
   *
   * @throws Exception
   */
  public void testRelationalReport() throws Exception
  {
    final TableModel tableModel = createRelationalTableModel();
    final MasterReport report = createRelationalReport(tableModel);

    // Null means the result is undefined.
    final Integer[][] rowAHeaderValues = {
        {5, 5, 5, 5, 5}, // page 0
        {3, 3, 4, 4},         // page 1
        {8, 8, 8}           // page 2
    };
    final Integer[][] rowBHeaderValues = {
        {null, 1, 1, 3, 0}, // page 0
        {null, 3, null, 4},         // page 1
        {1, 1, 7}           // page 2
    };
    final Integer[][] rowAFooterValues = {
        {5, 5, 5, 5, 5}, // page 0
        {3, 3, 4, 4},         // page 1
        {8, 8, 8}           // page 2
    };
    final Integer[][] rowBFooterValues = {
        {1, 1, 3, 0, 0}, // page 0
        {3, null, 4, null},         // page 1
        {1, 7, null}           // page 2
    };


    //report.addExpression(create("row-b-sum", ROW_DIMENSION_B));
    report.addExpression(create("row-b-sum", "::group-1"));
    report.addExpression(new ValidatePageFunctionResultExpression("#row-b-sum", null));

    //report.addExpression(create("row-a-sum", ROW_DIMENSION_A));
    report.addExpression(create("row-a-sum", "::group-0"));
    report.addExpression(new ValidatePageFunctionResultExpression("#row-a-sum", null));

    DebugReportRunner.showDialog(report);

    for (int page = 0; page < 3; page += 1)
    {
      final LogicalPageBox logicalPageBox = DebugReportRunner.layoutPage(report, page);
      validateItemBands(logicalPageBox);
      validateHeader(rowAHeaderValues[page], rowBHeaderValues[page], page, logicalPageBox);
      validateFooter(rowAFooterValues[page], rowBFooterValues[page], page, logicalPageBox);
    }


    // DebugReportRunner.execGraphics2D(report);
  }

  /**
   * Compares the header by their order of occurrence on the page. Each footer has text-fields which
   * carry their currently calculated value in their attributes. (This is done by a CopyValueAsTextExpression,
   * which is set up by the RelationalReportBuilder.)
   *
   * Each test maintains a human-provided list of expected values per page.
   *
   * @param rowAHeaderValue
   * @param rowBHeaderValue
   * @param page
   * @param logicalPageBox
   */
  private void validateHeader(final Integer[] rowAHeaderValue,
                              final Integer[] rowBHeaderValue,
                              final int page,
                              final LogicalPageBox logicalPageBox)
  {
    final RenderNode[] groupHeaders =
        MatchFactory.findElementsByElementType(logicalPageBox, GroupHeaderType.INSTANCE);
    for (int i = 0; i < groupHeaders.length; i++)
    {
      final RenderNode groupHeader = groupHeaders[i];
      final RenderNode rowASum = findTextFieldsWithField("row-a-sum", groupHeader);
      final RenderNode rowBSum = findTextFieldsWithField("row-b-sum", groupHeader);

      final Integer expectedA = rowAHeaderValue[i];
      final Object valueA = rowASum.getAttributes().getAttribute("test-run", "test-value");

      final Integer expectedB = rowBHeaderValue[i];
      final Object valueB = rowBSum.getAttributes().getAttribute("test-run", "test-value");

      try
      {
        if (expectedA != null)
        {
          assertEquals("Row-A", String.valueOf(expectedA), valueA);
        }
        if (expectedB != null)
        {
          assertEquals("Row-B", String.valueOf(expectedB), valueB);
        }
      }
      catch (AssertionFailedError afe)
      {
        DebugLog.log("Failed on page " + page);
        ModelPrinter.INSTANCE.print(groupHeader);
        throw afe;
      }
    }
  }

  /**
   * Compares the footer by their order of occurrence on the page. Each footer has text-fields which
   * carry their currently calculated value in their attributes. (This is done by a CopyValueAsTextExpression,
   * which is set up by the RelationalReportBuilder.)
   *
   * Each test maintains a human-provided list of expected values per page.
   *
   * @param rowAHeaderValue
   * @param rowBHeaderValue
   * @param page
   * @param logicalPageBox
   */
  private void validateFooter(final Integer[] rowAHeaderValue,
                              final Integer[] rowBHeaderValue,
                              final int page,
                              final LogicalPageBox logicalPageBox)
  {
    final RenderNode[] groupFooters =
        MatchFactory.findElementsByElementType(logicalPageBox, GroupFooterType.INSTANCE);
    for (int i = 0; i < groupFooters.length; i++)
    {
      final RenderNode groupFooter = groupFooters[i];
      final RenderNode rowASum = findTextFieldsWithField("row-a-sum", groupFooter);
      final RenderNode rowBSum = findTextFieldsWithField("row-b-sum", groupFooter);

      final Integer expectedA = rowAHeaderValue[i];
      final Object valueA = rowASum.getAttributes().getAttribute("test-run", "test-value");

      final Integer expectedB = rowBHeaderValue[i];
      final Object valueB = rowBSum.getAttributes().getAttribute("test-run", "test-value");

      try
      {
        if (expectedA != null)
        {
          assertEquals("Row-A", String.valueOf(expectedA), valueA);
        }
        if (expectedB != null)
        {
          assertEquals("Row-B", String.valueOf(expectedB), valueB);
        }
      }
      catch (AssertionFailedError afe)
      {
        DebugLog.log("Failed on page " + page);
        ModelPrinter.INSTANCE.print(groupFooter);
        throw afe;
      }
    }
  }

  /**
   * Compares the expression result directly with the result of the validation expression as found in the
   * detail band. For all Total*Page functions, this should always match.
   *
   * @param logicalPageBox
   */
  private void validateItemBands(final LogicalPageBox logicalPageBox)
  {
    final RenderNode[] itemBands = MatchFactory.findElementsByElementType(logicalPageBox, ItemBandType.INSTANCE);
    for (int i = 0; i < itemBands.length; i++)
    {
      final RenderNode itemBand = itemBands[i];
      final RenderNode rowASum = findTextFieldsWithField("row-a-sum", itemBand);
      final RenderNode rowBSum = findTextFieldsWithField("row-b-sum", itemBand);
      final RenderNode rowAValidate = findTextFieldsWithField("#row-a-sum", itemBand);
      final RenderNode rowBValidate = findTextFieldsWithField("#row-b-sum", itemBand);

      assertEquals(rowAValidate.getAttributes().getAttribute("test-run", "test-value"),
          rowASum.getAttributes().getAttribute("test-run", "test-value"));
      assertEquals(rowBValidate.getAttributes().getAttribute("test-run", "test-value"),
          rowBSum.getAttributes().getAttribute("test-run", "test-value"));
    }
  }

  private RenderNode findTextFieldsWithField(String field, RenderNode itemBand)
  {
    final ElementTypeMatcher typeMatcher = new ElementTypeMatcher(TextFieldType.INSTANCE.getMetaData().getName());
    final AttributeMatcher attributeMatcher = new AttributeMatcher(AttributeNames.Core.NAMESPACE, AttributeNames.Core.FIELD, field);
    final AndMatcher m = new AndMatcher(typeMatcher, attributeMatcher);
    return MatchFactory.match(itemBand, m);
  }

  private MasterReport createRelationalReport(final TableModel tableModel)
  {
    final MasterReport report = new MasterReport();
    report.setPageDefinition(new SimplePageDefinition(new PageSize(800, 300)));
    report.setDataFactory(new TableDataFactory("query", tableModel));
    report.setQuery("query");

    final DesignTimeDataSchemaModel dataSchemaModel = new DesignTimeDataSchemaModel(report);
    final RelationalReportBuilder builder = new RelationalReportBuilder(dataSchemaModel);
    builder.addGroup(ROW_DIMENSION_A);
    builder.addGroup(ROW_DIMENSION_B);
    builder.addDetails(VALUE, TotalPageItemCountFunction.class, VALUE_BACKGROUND);
    builder.addDetails("row-a-sum", null, ROWA_BACKGROUND);
    builder.addDetails("#row-a-sum", null, ROWA_VALIDATE_BACKGROUND);
    builder.addDetails("row-b-sum", null, ROWB_BACKGROUND);
    builder.addDetails("#row-b-sum", null, ROWB_VALIDATE_BACKGROUND);

    report.setRootGroup(builder.create());


    return report;
  }
}
