package com.rockthejvm.reviewboard.services

import com.rockthejvm.reviewboard.domain.data.{User, UserToken}
import com.rockthejvm.reviewboard.repositories.{RecoveryTokensRepository, UserRepository}
import zio.*

import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

trait UserService:
  def registerUser(email: String, password: String): Task[User]
  def verifyPassword(email: String, password: String): Task[Boolean]
  def generateToken(email: String, password: String): Task[UserToken]
  def updatePassword(email: String, oldPassword: String, newPassword: String): Task[User]
  def deleteUser(email: String, password: String): Task[User]
  def sendRecoveryToken(email: String): Task[Unit]
  def recoverFromToken(email: String, token: String, newPassword: String): Task[Boolean]

class UserServiceLive private (
  jwtService: JwtService,
  emailService: EmailService,
  userRepo: UserRepository,
  tokenRepo: RecoveryTokensRepository
) extends UserService:
  override def registerUser(email: String, password: String): Task[User] =
    userRepo.create(User(-1L, email, UserServiceLive.Hasher.generateHash(password)))

  override def verifyPassword(email: String, password: String): Task[Boolean] =
    verifyUser(email, password).isSuccess

  override def generateToken(email: String, password: String): Task[UserToken] =
    for
      user  <- verifyUser(email, password)
      token <- jwtService.createToken(user)
    yield token

  override def updatePassword(email: String, oldPassword: String, newPassword: String): Task[User] =
    import UserServiceLive.Hasher.generateHash
    for
      user    <- verifyUser(email, oldPassword)
      updated <- userRepo.update(user.id, _.copy(hashedPassword = generateHash(newPassword)))
    yield updated

  override def deleteUser(email: String, password: String): Task[User] =
    for
      user    <- verifyUser(email, password)
      deleted <- userRepo.delete(user.id)
    yield deleted

  private def verifyUser(email: String, password: String): Task[User] =
    for
      user         <- userRepo.getByEmail(email).someOrFail(new RuntimeException("bad email"))
      verified     <- ZIO.attempt(UserServiceLive.Hasher.validateHash(password, user.hashedPassword))
      verifiedUser <- ZIO.attempt(user).when(verified).someOrFail(new RuntimeException("bad password"))
    yield verifiedUser

  override def sendRecoveryToken(email: String): Task[Unit] =
    tokenRepo.getToken(email).flatMap:
      case Some(token) => emailService.sendPasswordRecovery(email, token)
      case None => ZIO.unit

  override def recoverFromToken(email: String, token: String, newPassword: String): Task[Boolean] =
    import UserServiceLive.Hasher.generateHash
    for
      user   <- userRepo.getByEmail(email).someOrFail(new RuntimeException("bad email"))
      valid  <- tokenRepo.checkToken(email, token)
      result <- userRepo
        .update(user.id, _.copy(hashedPassword = generateHash(newPassword)))
        .when(valid)
        .map(_.nonEmpty)
    yield result

object UserServiceLive:
  private type R = UserRepository & RecoveryTokensRepository & EmailService & JwtService
  val layer: ZLayer[R, Nothing, UserServiceLive] = ZLayer:
    for
      jwtService   <- ZIO.service[JwtService]
      emailService <- ZIO.service[EmailService]
      userRepo     <- ZIO.service[UserRepository]
      tokenRepo    <- ZIO.service[RecoveryTokensRepository]
    yield UserServiceLive(jwtService, emailService, userRepo, tokenRepo)

  private object Hasher:
    def generateHash(str: String): String =
      val rng = new SecureRandom()
      val salt = Array.ofDim[Byte](SALT_BYTE_SIZE)
      rng.nextBytes(salt)
      val hashBytes = pbkdf2(str.toCharArray, salt, N_ITERATIONS, HASH_BYTE_SIZE)
      s"$N_ITERATIONS:${toHex(salt)}:${toHex(hashBytes)}"

    def validateHash(test: String, hash: String): Boolean =
      val hashSections = hash.split(":")
      val nIters = hashSections(0).toInt
      val salt = fromHex(hashSections(1))
      val validHash = fromHex(hashSections(2))
      val testHash = pbkdf2(test.toCharArray, salt, nIters, HASH_BYTE_SIZE)
      compareBytes(testHash, validHash)

    private val PBKDF2_ALGORITHM: String = "PBKDF2WithHmacSHA512"
    private val N_ITERATIONS: Int = 1000
    private val SALT_BYTE_SIZE: Int = 24
    private val HASH_BYTE_SIZE: Int = 24
    private val skf = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)

    private def pbkdf2(msg: Array[Char], salt: Array[Byte], i: Int, nBytes: Int): Array[Byte] =
      val keySpec = PBEKeySpec(msg, salt, i, nBytes * 8)
      skf.generateSecret(keySpec).getEncoded

    private def toHex(arr: Array[Byte]): String =
      arr.map("%02X".format(_)).mkString

    private def fromHex(str: String): Array[Byte] =
      str.grouped(2).toArray.map(Integer.parseInt(_, 16).toByte)

    private def compareBytes(a: Array[Byte], b: Array[Byte]): Boolean =
      val diff =
        (0 until (a.length min b.length))
          .foldLeft(a.length ^ b.length): (acc, i) =>
            acc | (a(i) ^ b(i))
      diff == 0
  end Hasher
end UserServiceLive
