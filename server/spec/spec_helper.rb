require 'base64'
require 'zip/zip'

require_relative './contexts'
require_relative '../client/ruby/candlepin'

module SpecUtils
  RANDOM_CHARS = [('a'..'z'), ('A'..'Z'), ('1'..'9')].map(&:to_a).flatten

  def rand_string(prefix = '', len = 9)
    rand = (0...len).map { RANDOM_CHARS[rand(RANDOM_CHARS.length)] }.join
    prefix.empty? ? rand : "#{prefix}-#{rand}"
  end

  def flatten_attributes(attributes)
    attributes.each_with_object({}) do |entry, result|
      result[entry[:name].to_sym] = entry[:value]
    end
  end

  def parse_file(filename)
    JSON.parse(File.read(filename))
  end

  def files_in_dir(dir_name)
    Dir.entries(dir_name).reject do |e|
      e == '.' || e == '..'
    end
  end

  def find_guest_virt_pool(client, guest_uuid)
    pools = client.list_pools(:consumer => guest_uuid).ok_content
    return pools.detect do |i|
      !i[:sourceEntitlement].nil?
    end
  end

  def filter_unmapped_guest_pools(pools)
    # need to ignore the unmapped guest pools
    pools.select! do |p|
      unmapped = p[:attributes].detect do |i|
        i[:name] == 'unmapped_guests_only'
      end
      unmapped.nil? || unmapped['value'] == 'false'
    end
  end

  def extract_payload(certificate)
    payload = certificate.split("-----BEGIN ENTITLEMENT DATA-----\n")[1]
    payload = payload.split("-----END ENTITLEMENT DATA-----")[0]
    asn1_body = Base64.decode64(payload)
    body = Zlib::Inflate.inflate(asn1_body)
    JSON.parse(body)
  end

  def new_owner(key = nil)
    key ||= rand_string('owner')
    user_client.create_owner(
      :owner => key,
      :display_name => key,
    ).ok_content
  end

  def new_owner_user(owner, super_admin = false)
    key = owner.is_a?(Hash) ? owner[:key] : owner
    user_client.create_user_under_owner(
      :username => rand_string('owner_user'),
      :password => rand_string,
      :owner => key,
      :super_admin => super_admin,
    )
  end
end

module CleanupHooks
  include SpecUtils

  def cleanup_before
    @cp = Candlepin::BasicAuthClient.new
    @owners = []
    @created_products = []
    @dist_versions = []
    @users = []
    @roles = []
    @rules = nil
    @cdns = []
  end

  def cleanup_after
    @roles.reverse_each do |r|
      @cp.delete_role(:role_id => r[:id])
    end
    @owners.reverse_each do |owner|
      @cp.delete_owner(:owner => owner[:key])
    end
    @users.reverse_each do |user|
      @cp.delete_user(:username => user[:username])
    end
    @dist_versions.reverse_each do |dist|
      @cp.delete_distributor_version(:id => dist[:id])
    end
    @cdns.reverse_each do |cdn|
      @cp.delete_cdn(:lable => cdn[:label])
    end

    # restore the original rules
    @cp.delete_rules if @rules
    status = @cp.get_status.ok_content
    unless status[:standalone]
      begin
        @cp.delete('/hostedtest/subscriptions/')
      rescue
        puts "Skipping hostedtest cleanup"
      end
    end
  end
end

RSpec.configure do |config|
  # disallow RSpec's legacy "should" tests
  config.expect_with :rspec do |c|
    c.syntax = :expect
  end

  # Sometimes when diagnosing a test failure, you might not want to
  # run the :after hook so you can do a post-mortem.  If that's the case
  # set this value to false using an environment variable.
  config.add_setting(:run_after_hook, :default => true)
  RSpec.configuration.run_after_hook = false if ENV['run_after_hook'] == 'false'

  include CleanupHooks

  config.before(:each) do
    cleanup_before
  end

  config.after(:each) do
    if RSpec.configuration.run_after_hook?
      cleanup_after
    end
  end
end

RSpec::Matchers.define :be_success do
  match do |res|
    (200..206).cover?(res.status_code)
  end
end

RSpec::Matchers.define :be_unauthorized do
  match do |res|
    res.status_code == 401
  end
end

RSpec::Matchers.define :be_forbidden do
  match do |res|
    res.status_code == 403
  end
end

RSpec::Matchers.define :be_missing do
  match do |res|
    res.status_code == 404
  end
end
