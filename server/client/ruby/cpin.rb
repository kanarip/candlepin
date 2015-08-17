#! /usr/bin/env ruby

require 'active_support/all'
require 'awesome_print'
require 'json'
require 'thor'
require 'uri'
require 'yaml'

require_relative './candlepin.rb'


module Candlepin
  class CPin < Thor
    DEFAULT_RC = File.join(ENV['HOME'], '.cpinrc')

    class_option :config, :aliases => ['-c'],
      :default => DEFAULT_RC

    class_option :verbose, :aliases => ['-v'],
      :type => :boolean

    class_option :debug,
      :type => :boolean

    class_option :server_url,
      :default => "https://localhost:8443/candlepin"

    class_option :server_ca,
      :default => "/etc/candlepin/certs/candlepin-ca.crt"

    class_option :insecure,
      :desc => "Disable SSL verifications (defaults to false if --server-url is localhost)",
      :type => :boolean

    class_option :pretty_print,
      :desc => "Pretty print JSON responses",
      :type => :boolean,
      :default => true

    attr_accessor :rc_file
    attr_accessor :verbose
    attr_accessor :debug

    @@rc_whitelist = [:verbose, :debug]

    def initialize(args, local_options, config)
      super
      read_rc_file(@options['config'])
    end

    desc "status", "Return Candlepin status"
    def status
      c = get_client
      json_out(c.get_status.content)
    end

    #Methods in the no_commands block are not exposed to users
    no_commands do
      def verbose?
        verbose
      end

      def debug?
        debug
      end

      def get_client(opts = {})
        begin
          uri = URI.parse(@options["server_url"])
        rescue URI::InvalidURIError => e
          raise Thor::Error.new(e.message)
        end
        insecure = @options["insecure"]
        insecure = true if uri.host == "localhost"

        args = {
          :host => uri.host,
          :port => uri.port,
          :context => uri.path,
          :insecure => insecure,
          :ca_path => @options["server_ca"],
        }

        if opts.empty?
          client = NoAuthClient.new(args)
        end

        client.debug if debug?
        client
      end

      def self.source_root
        File.dirname(__FILE__)
      end

      def json_out(content)
        if @options["pretty_print"]
          # Negative indentation left-justifies the output
          formatted = content.awesome_inspect(:indent => -2, :index => false)
          say(formatted)
        else
          say(content)
        end
      end

      def warn(msg)
        say("WARNING: #{msg}", :yellow)
      end

      def read_rc_file(rc_file)
        if File.exist?(rc_file)
          rc_settings = YAML.load(File.open(rc_file, 'r'))
          rc_settings.deep_symbolize_keys!
        else
          require 'pry'; binding.pry
          warn("Cannot find #{rc_file}!")
          rc_settings = {}
        end

        bad_settings = []
        rc_settings.each do |k, v|
          if @@rc_whitelist.include?(k)
            send(:"#{k}=", v)
          else
            bad_settings << k
          end
        end
        unless bad_settings.empty?
          raise Thor::Error.new("The cpinrc file does not support these settings: #{bad_settings}")
        end
      end
    end
  end
end
