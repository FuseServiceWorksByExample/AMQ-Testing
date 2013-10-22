One JMS client pushing messages to the virtual topic named "VirtualTopic.TEST.JMS".
Ten JMS clients receiving messages from the queue named "Consumer.A.VirtualTopic.TEST.JMS". These clients compete so each one will receive one tenth of the total messages published.
