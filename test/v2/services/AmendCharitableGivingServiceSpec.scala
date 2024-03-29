/*
 * Copyright 2021 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package v2.services

import uk.gov.hmrc.domain.Nino
import v2.mocks.connectors.MockDesConnector
import v2.models.domain.{CharitableGiving, GiftAidPayments, Gifts}
import v2.models.errors._
import v2.models.outcomes.DesResponse
import v2.models.requestData.{AmendCharitableGivingRequest, DesTaxYear}

import scala.concurrent.Future

class AmendCharitableGivingServiceSpec extends ServiceSpec {

  trait Test extends MockDesConnector {
    lazy val target = new CharitableGivingService(connector)
  }


  implicit val correlationId: String = "X-123"
  val expectedRef = "000000000001013"
  val nino = "AA123456A"
  val desTaxYear = "2018"
  val expectedDesResponse = DesResponse(correlationId, expectedRef)
  val input = AmendCharitableGivingRequest(Nino(nino), DesTaxYear(desTaxYear),
    CharitableGiving(Some(GiftAidPayments(None, None, None, None, None, None)), Some(Gifts(None, None, None, None))))

  "calling amend" should {
    "return a valid correlationId" when {
      "a valid data is passed" in new Test {

        MockedDesConnector.amend(input).returns(Future.successful(Right(expectedDesResponse)))

        private val result = await(target.amend(input))

        result shouldBe Right(expectedDesResponse)
      }
    }

    "return multiple errors" when {
      "the DesConnector returns multiple errors" in new Test {

        val response = DesResponse(correlationId,
          MultipleErrors(Seq(Error("INVALID_NINO", "doesn't matter"), Error("INVALID_TAXYEAR", "doesn't matter"))))

        val expected = ErrorWrapper(correlationId, BadRequestError, Some(Seq(NinoFormatError, TaxYearFormatError)))

        MockedDesConnector.amend(input).returns(Future.successful(Left(response)))

        private val result = await(target.amend(input))
        result shouldBe Left(expected)
      }

    }
    "return a single error" when {
      "the DesConnector returns multiple errors and one maps to a DownstreamError" in new Test {
        val response = DesResponse(correlationId,
          MultipleErrors(Seq(Error("INVALID_NINO", "doesn't matter"), Error("INVALID_TYPE", "doesn't matter"))))

        val expected = ErrorWrapper(correlationId, DownstreamError, None)

        MockedDesConnector.amend(input).returns(Future.successful(Left(response)))

        private val result = await(target.amend(input))
        result shouldBe Left(expected)
      }
    }

    "the DesConnector returns a GenericError" in new Test {
      val response = DesResponse(correlationId, OutboundError(DownstreamError))

      val expected = ErrorWrapper(correlationId, DownstreamError, None)

      MockedDesConnector.amend(input).returns(Future.successful(Left(response)))

      private val result = await(target.amend(input))
      result shouldBe Left(expected)
    }

    val errorMap: Map[String, Error] = Map(
      "INVALID_NINO" -> NinoFormatError,
      "INVALID_TYPE" -> DownstreamError,
      "INVALID_TAXYEAR" -> TaxYearFormatError,
      "INVALID_PAYLOAD" -> BadRequestError,
      "INVALID_ACCOUNTING_PERIOD" -> TaxYearNotSupportedRuleError,
      "MISSING_CHARITIES_NAME_GIFT_AID" -> NonUKNamesNotSpecifiedRuleError,
      "MISSING_GIFT_AID_AMOUNT" -> NonUKAmountNotSpecifiedRuleError,
      "MISSING_CHARITIES_NAME_INVESTMENT" -> NonUKInvestmentsNamesNotSpecifiedRuleError,
      "MISSING_INVESTMENT_AMOUNT" -> NonUKInvestmentAmountNotSpecifiedRuleError,
      "SERVER_ERROR" -> DownstreamError,
      "SERVICE_UNAVAILABLE" -> DownstreamError
    )


    for (error <- errorMap.keys) {

      s"the DesConnector returns a single $error error" in new Test {

        val response = DesResponse(correlationId, SingleError(Error(error, "doesn't matter")))

        val expected = ErrorWrapper(correlationId, errorMap(error), None)

        MockedDesConnector.amend(input).returns(Future.successful(Left(response)))

        private val result = await(target.amend(input))
        result shouldBe Left(expected)
      }

    }

  }
}
