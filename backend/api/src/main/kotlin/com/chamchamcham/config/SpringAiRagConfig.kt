package com.chamchamcham.config

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.model.ChatModel
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SpringAiRagConfig {
    @Bean
    fun chatClient(chatModel: ChatModel): ChatClient {
        return ChatClient.create(chatModel)
    }
}
