package dappermongo.aggregate

import dappermongo.MongoITSpecDefault
import zio.test.Assertion.{endsWithString, isLeft, isRight}
import zio.test._

object AggregateSpec extends MongoITSpecDefault {

  val spec = suite("Aggregate")(
    suite("operators")(
      suite("composition")(
        test("combine operators") {
          assertZIO(typeCheck("""
             import reactivemongo.api.bson.document
             import Stage._

             val s1 = `match`(document("a" -> 1)) >>> limit(1)
             val s2 = `match`(document("b" -> 2))
             s1 >>> s2
            """))(isRight)
        },
        test("compile time error when combining incompatible operators") {
          assertZIO(typeCheck("""
                 import reactivemongo.api.bson.document
                 import search.{SearchOperator, SearchPath}
                 import Stage._

                 val s1 = `match`(document("a" -> 1))
                 val s2 = search(SearchOperator.AutoComplete(List("a"), SearchPath.field("a")))
                 s1 >>> s2
                """))(
            isLeft(endsWithString("must be the first stage in a pipeline"))
          )
        } @@ TestAspect.scala2Only
      )
    )
  )

}
