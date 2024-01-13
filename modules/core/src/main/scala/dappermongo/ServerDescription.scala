package dappermongo

import com.mongodb.connection.{ServerDescription => JServerDescription}

class ServerDescription(private[dappermongo] val wrapped: JServerDescription)
