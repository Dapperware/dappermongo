package zio.mongo

import com.mongodb.connection.{ServerDescription => JServerDescription}

class ServerDescription(private[mongo] val wrapped: JServerDescription)
