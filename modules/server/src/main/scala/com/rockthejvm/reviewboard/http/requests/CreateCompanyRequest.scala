package com.rockthejvm.reviewboard.http.requests

import com.rockthejvm.reviewboard.domain.data.Company
import zio.json.{DeriveJsonCodec, JsonCodec}

/** the body of a create company request */
final case class CreateCompanyRequest(
  name: String,
  url: String,
  location: Option[String] = None,
  country: Option[String] = None,
  industry: Option[String] = None,
  image: Option[String] = None,
  tags: Option[List[String]] = None
):
  def toCompany(id: Long): Company =
    Company(
      id,
      Company.makeSlug(name),
      name,
      url,
      location,
      country,
      industry,
      image,
      tags.getOrElse(Nil)
    )
end CreateCompanyRequest

object CreateCompanyRequest:
  given codec: JsonCodec[CreateCompanyRequest] =
    DeriveJsonCodec.gen[CreateCompanyRequest]
