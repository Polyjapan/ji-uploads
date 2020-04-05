package models

import java.sql.Connection

import anorm.Macro.ColumnNaming
import anorm.SqlParser.scalar
import anorm.{BatchSql, NamedParameter, ParameterValue, SQL, ToParameterList}

object SqlUtils {
  /**
   * Inserts one item in the given table and returns its id
   *
   * @param table the table in which the item shall be inserted
   * @param item  the item that shall be inserted
   * @return the id of the inserted item
   */
  def insertOne[T](table: String, item: T)(implicit parameterList: ToParameterList[T], conn: Connection): Int = {
    val params: Seq[NamedParameter] = parameterList(item);
    val names: List[String] = params.map(_.name).toList
    val colNames = names.map(ColumnNaming.SnakeCase) mkString ", "
    val placeholders = names.map { n => s"{$n}" } mkString ", "

    SQL("INSERT INTO " + table + "(" + colNames + ") VALUES (" + placeholders + ")")
      .bind(item)
      .executeInsert(scalar[Int].single)
  }

  /**
   * Inserts items in the given table
   *
   * @param table the table in which the items shall be inserted
   * @param items the items that shall be inserted
   */
  def insertMultiple[T](table: String, items: Iterable[T])(implicit parameterList: ToParameterList[T], conn: Connection) = {
    val params: Seq[NamedParameter] = parameterList(items.head);
    val names: List[String] = params.map(_.name).toList
    val colNames = names.map(ColumnNaming.SnakeCase) mkString ", "
    val placeholders = names.map { n => s"{$n}" } mkString ", "

    BatchSql("INSERT INTO " + table + "(" + colNames + ") VALUES (" + placeholders + ")", params, items.tail.map(parameterList).toSeq:_*)
      .execute()
  }
}
