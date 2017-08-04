def call(String requester) {

  def trigger_user = ''

  wrap([$class: 'BuildUser']) {

    trigger_user = env.BUILD_USER_ID

    // decide trigger by github or not

    // decide trigger by slack or not

    // decide trigger by other jenkins job or not

    echo "This job is triggered by ${trigger_user}..."
  }

  return trigger_user
}
