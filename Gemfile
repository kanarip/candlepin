source 'https://rubygems.org'

gem 'buildr', '1.4.19'
# Not a typo - we use both buildr and buildr
gem 'builder'
gem 'rspec'
gem 'mime-types', '~> 1.25.0'
gem 'oauth'
gem 'parallel_tests'
gem 'buildr-findBugs'
gem 'pmd'
gem 'stringex'
gem 'httpclient'
gem 'activesupport'

group :development, :test do
  gem 'webrick'
  gem 'pry'
  gem 'pry-byebug'
  gem 'pry-stack_explorer'
  gem 'fuubar'
  # Rubocop can add new checks in new releases which
  # can result in new errors, so we control the version
  # very strictly
  gem 'rubocop', '=0.36.0'
end
