package com.senderman.miniroulette

import com.annimon.tgbotsmodule.BotHandler
import com.annimon.tgbotsmodule.api.methods.Methods
import com.senderman.miniroulette.gameobjects.Game
import org.telegram.telegrambots.meta.api.methods.BotApiMethod
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.HashMap

class RouletteBotHandler : BotHandler(), MainHandler {

    private val games: MutableMap<Long, Game>
    private val executorKeeper: ExecutorKeeper

    init {
        Services.db = MongoDBService()
        games = HashMap()
        executorKeeper = ExecutorKeeper(this)
    }

    override fun onUpdate(update: Update): BotApiMethod<*>? {

        if (!update.hasMessage()) return null

        val message = update.message
        // don't handle old messages
        if (message.date + TimeUnit.MINUTES.toSeconds(2) < System.currentTimeMillis() / 1000) return null

        if (!message.hasText()) return null

        val text = message.text

        // make bet
        if (text.toLowerCase().matches("\\d+\\s+(?:ч(?:[её]рное)?|к(?:расное)?|\\d{1,2}(:?-\\d{1,2})?)".toRegex()) &&
            containsGame(message.chatId)
        ) {
            getGame(message.chatId)!!.addBet(message.from.id, message.from.firstName, text, message.messageId)
        }

        if (!message.isCommand) return null

        /* bot should only trigger on general commands (like /command) or on commands for this bot (/command@mybot),
         * and NOT on commands for another bots (like /command@notmybot)
         */
        val command = text.split("\\s+".toRegex(), 2)[0]
            .toLowerCase(Locale.ENGLISH)
            .replace("@$botUsername", "")
        if ("@" in command) return null

        executorKeeper.findExecutor(command)?.let { executor ->
            if (message.from.id != Services.botConfig.mainAdmin && executor.forMainAdmin) return null
            executor.execute(message)
        }

        return null
    }

    override fun sendMessage(chatId: Long, text: String, replyToMessageId: Int?): Message =
        Methods.sendMessage(chatId, text)
            .setReplyToMessageId(replyToMessageId)
            .enableHtml()
            .disableWebPagePreview()
            .call(this)

    override fun deleteMessage(chatId: Long, messageId: Int) {
        try {
            val delMsg = DeleteMessage(chatId, messageId)
            execute(delMsg)
        } catch (e: TelegramApiException) {
            // nothing here
        }
    }

    override fun getBotUsername(): String = Services.botConfig.login.split(" ".toRegex(), 2)[0]

    override fun getBotToken(): String = Services.botConfig.login.split(" ".toRegex(), 2)[1]

    override fun addGame(game: Game) {
        games[game.chatId] = game
    }

    override fun removeGame(game: Game) {
        games.remove(game.chatId)
    }

    override fun getGame(chatId: Long): Game? = games[chatId]

    override fun containsGame(chatId: Long): Boolean = chatId in games
}