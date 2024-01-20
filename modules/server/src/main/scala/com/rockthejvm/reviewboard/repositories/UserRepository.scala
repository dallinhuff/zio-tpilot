package com.rockthejvm.reviewboard.repositories

import com.rockthejvm.reviewboard.domain.data.User
import zio.*
import io.getquill.*
import io.getquill.jdbczio.Quill

/** Data access layer for users */
trait UserRepository extends Repository[User]:
  /** Get user by their email address
    * @param email
    *   the email address to search with
    * @return
    *   a task containing an option of the user with the given email
    */
  def getByEmail(email: String): Task[Option[User]]
end UserRepository

/** Implementation of UserRepository with Quill and Postgres
  * @param quill
  *   the quill instance to run queries with
  */
class UserRepositoryLive private (quill: Quill.Postgres[SnakeCase])
    extends UserRepository:
  import quill.*

  inline given schema: SchemaMeta[User]  = schemaMeta[User]("users")
  inline given insMeta: InsertMeta[User] = insertMeta[User](_.id)
  inline given upMeta: UpdateMeta[User]  = updateMeta[User](_.id)

  override def getByEmail(email: String): Task[Option[User]] =
    run(query[User].filter(_.email == lift(email))).map(_.headOption)

  override def create(user: User): Task[User] =
    run(query[User].insertValue(lift(user)).returning(u => u))

  override def update(id: Long, op: User => User): Task[User] =
    for
      curr <- getById(id).someOrFail(new RuntimeException("boo"))
      updated <- run:
        query[User]
          .filter(_.id == lift(id))
          .updateValue(lift(op(curr)))
          .returning(u => u)
    yield updated

  override def delete(id: Long): Task[User] =
    run(query[User].filter(_.id == lift(id)).delete.returning(u => u))

  override def getById(id: Long): Task[Option[User]] =
    run(query[User].filter(_.id == lift(id))).map(_.headOption)

  override def getAll: Task[List[User]] =
    run(query[User])
end UserRepositoryLive

object UserRepositoryLive:
  val layer: ZLayer[Quill.Postgres[SnakeCase], Nothing, UserRepositoryLive] =
    ZLayer:
      ZIO
        .service[Quill.Postgres[SnakeCase]]
        .map(new UserRepositoryLive(_))
end UserRepositoryLive
